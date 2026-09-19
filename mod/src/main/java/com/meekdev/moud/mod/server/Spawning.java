package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.character.HumanoidState;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.character.Tools;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.SpawnLocation;
import com.meekdev.moud.core.player.Team;
import com.meekdev.moud.mod.adapter.physics.Characters;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.place.Place;
import com.meekdev.moud.mod.transport.payload.ControlsPayload;
import com.meekdev.moud.script.api.ControlsRef;
import com.meekdev.moud.script.api.SpawnRef;
import com.meekdev.moud.script.host.Host;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

public final class Spawning {

    public static final Vector3 FALLBACK = new Vector3(0.5, 70, 0.5);

    private static final class Rules implements SpawnRef {
        private boolean autoSpawn = true;
        private double respawnTime = 5;

        @Override
        public boolean autoSpawn() {
            return autoSpawn;
        }

        @Override
        public void autoSpawn(boolean on) {
            autoSpawn = on;
        }

        @Override
        public double respawnTime() {
            return respawnTime;
        }

        @Override
        public void respawnTime(double seconds) {
            respawnTime = seconds;
        }
    }

    private record Point(Vector3 position, double yawDegrees) {}

    private static Rules rules = new Rules();
    private static final Map<UUID, Double> dead = new HashMap<>();
    private static final Set<UUID> waiting = new HashSet<>();
    private static final Map<UUID, Map<String, Boolean>> controls = new HashMap<>();

    private Spawning() {}

    public static SpawnRef fresh() {
        rules = new Rules();
        controls.clear();
        return rules;
    }

    static boolean autoSpawn() {
        return rules.autoSpawn;
    }

    static void spawn(ServerPlayer player, @Nullable Vector3 at) {
        if (place(player, at)) announce(player);
    }

    static boolean place(ServerPlayer player, @Nullable Vector3 at) {
        Instance world = ServerScene.world();
        InstanceTree tree = ServerScene.tree();
        if (world == null || tree == null) return false;
        boolean fresh = false;
        Point point = at != null ? new Point(at, player.getYRot()) : point(tree, Teams.of(player));
        Character character = Physics.bodies().of(player, tree);
        if (character != null && Rig.humanoid(character) instanceof Humanoid humanoid && humanoid.state == HumanoidState.DEAD) {
            Physics.bodies().release(player);
            Instances.destroy(character);
            character = null;
        }
        if (character == null) {
            character = Instances.create(Classes.CHARACTER, world, player.getGameProfile().name());
            Physics.bodies().bind(player, character);
            Tools.stock(character, tree, Addons.classes());
            Teams.mirror(player);
            fresh = true;
        }
        player.teleportTo(player.level(), point.position().x(), point.position().y(), point.position().z(), Set.of(), (float) point.yawDegrees(), player.getXRot(), true);
        Characters.place(character, point.position(), point.yawDegrees());
        dead.remove(player.getUUID());
        if (waiting.remove(player.getUUID())) send(player);
        return fresh;
    }

    static void announce(ServerPlayer player) {
        Place place = MoudServer.place();
        Host host = place == null ? null : place.host();
        if (host != null) host.spawned(new JoinedPlayer(player));
    }

    static void respawned(ServerPlayer player) {
        InstanceTree tree = ServerScene.tree();
        if (tree == null) return;
        if (Physics.bodies().of(player, tree) != null || rules.autoSpawn) spawn(player, null);
        else hold(player);
        send(player);
    }

    static void hold(ServerPlayer player) {
        InstanceTree tree = ServerScene.tree();
        if (tree == null) return;
        Point point = point(tree, Teams.of(player));
        player.teleportTo(player.level(), point.position().x(), point.position().y(), point.position().z(), Set.of(), (float) point.yawDegrees(), player.getXRot(), true);
        waiting.add(player.getUUID());
        send(player);
    }

    static void worldSpawn(ServerLevel level, @Nullable InstanceTree tree) {
        if (tree == null) return;
        for (SpawnLocation location : tree.ofClass(Classes.SPAWN_LOCATION)) {
            if (!location.enabled || !location.isAlive() || Instance.outOfWorld(location)) continue;
            Vector3 at = Transforms.world(location).position();
            BlockPos pos = BlockPos.containing(at.x(), at.y() + location.size.y() / 2, at.z());
            if (!pos.equals(level.getRespawnData().pos())) level.setRespawnData(LevelData.RespawnData.of(level.dimension(), pos, 0, 0));
            return;
        }
    }

    static void tick(MinecraftServer server, double dt) {
        InstanceTree tree = ServerScene.tree();
        if (tree == null) return;
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            Character character = Physics.bodies().of(player, tree);
            if (character == null || !(Rig.humanoid(character) instanceof Humanoid humanoid) || humanoid.state != HumanoidState.DEAD) {
                dead.remove(player.getUUID());
                continue;
            }
            double since = dead.merge(player.getUUID(), dt, Double::sum);
            if (rules.autoSpawn && since >= rules.respawnTime) spawn(player, null);
        }
    }

    private static final Map<UUID, Integer> humanoidHolds = new HashMap<>();
    private static final int HOLD_MOVE = 1;
    private static final int HOLD_JUMP = 2;

    static void humanoid(ServerPlayer player, boolean move, boolean jump) {
        int holds = (move ? 0 : HOLD_MOVE) | (jump ? 0 : HOLD_JUMP);
        Integer before = humanoidHolds.put(player.getUUID(), holds);
        if (before == null ? holds != 0 : before != holds) send(player);
    }

    static void left(ServerPlayer player) {
        humanoidHolds.remove(player.getUUID());
        dead.remove(player.getUUID());
        waiting.remove(player.getUUID());
        controls.remove(player.getUUID());
    }

    static ControlsRef controls(ServerPlayer player) {
        return new ControlsRef() {
            @Override
            public boolean enabled(String control) {
                check(control);
                return controls.getOrDefault(player.getUUID(), Map.of()).getOrDefault(control, true);
            }

            @Override
            public void enabled(String control, boolean on) {
                check(control);
                controls.computeIfAbsent(player.getUUID(), key -> new HashMap<>()).put(control, on);
                send(player);
            }
        };
    }

    private static void check(String control) {
        if (!ControlsRef.NAMES.contains(control)) throw new IllegalArgumentException("'" + control + "' is not a control, expected move, jump or look");
    }

    private static void send(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, ControlsPayload.TYPE)) return;
        Map<String, Boolean> set = controls.getOrDefault(player.getUUID(), Map.of());
        boolean held = waiting.contains(player.getUUID());
        int holds = humanoidHolds.getOrDefault(player.getUUID(), 0);
        ServerPlayNetworking.send(player, new ControlsPayload(!held && (holds & HOLD_MOVE) == 0 && set.getOrDefault("move", true),
                !held && (holds & HOLD_JUMP) == 0 && set.getOrDefault("jump", true), set.getOrDefault("look", true)));
    }

    private static Point point(InstanceTree tree, @Nullable Team team) {
        List<SpawnLocation> open = new ArrayList<>();
        List<SpawnLocation> anyTeam = new ArrayList<>();
        for (SpawnLocation location : tree.ofClass(Classes.SPAWN_LOCATION)) {
            if (!location.enabled || !location.isAlive() || Instance.outOfWorld(location)) continue;
            anyTeam.add(location);
            if (Teams.allowed(location, team)) open.add(location);
        }
        if (open.isEmpty()) open = anyTeam;
        if (open.isEmpty()) return new Point(FALLBACK, 0);
        SpawnLocation chosen = open.get(ThreadLocalRandom.current().nextInt(open.size()));
        CFrame frame = Transforms.world(chosen);
        Vector3 top = frame.mul(CFrame.at(0, chosen.size.y() * 0.5, 0)).position();
        Vector3 facing = frame.vectorToWorld(new Vector3(0, 0, -1));
        double yaw = Math.toDegrees(Math.atan2(-facing.x(), facing.z()));
        return new Point(top, yaw);
    }
}
