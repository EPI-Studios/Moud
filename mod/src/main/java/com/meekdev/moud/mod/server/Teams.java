package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.input.ClickDetector;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.SpawnLocation;
import com.meekdev.moud.core.player.Team;
import com.meekdev.moud.mod.adapter.physics.Physics;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

public final class Teams {

    private static final PropertyDef TEAM = Classes.CHARACTER.property("team");
    private static final double PAD_HEIGHT = 1.5;

    private static final Map<UUID, Team> CHOSEN = new HashMap<>();

    private Teams() {}

    public static @Nullable Team of(ServerPlayer player) {
        Team team = CHOSEN.get(player.getUUID());
        return team != null && team.isAlive() ? team : null;
    }

    static void joined(ServerPlayer player) {
        InstanceTree tree = ServerScene.tree();
        if (tree == null || of(player) != null) return;
        List<Team> open = new ArrayList<>();
        for (Team team : tree.ofClass(Classes.TEAM)) {
            if (team.autoAssignable && !Instance.outOfWorld(team)) open.add(team);
        }
        if (open.isEmpty()) return;
        int fewest = Integer.MAX_VALUE;
        List<Team> smallest = new ArrayList<>();
        for (Team team : open) {
            int size = size(team);
            if (size < fewest) {
                fewest = size;
                smallest.clear();
            }
            if (size == fewest) smallest.add(team);
        }
        set(player, smallest.get(ThreadLocalRandom.current().nextInt(smallest.size())));
    }

    public static void set(ServerPlayer player, @Nullable Instance next) {
        Team team = next instanceof Team chosen && chosen.isAlive() ? chosen : null;
        Team before = of(player);
        if (before == team) return;
        if (team == null) CHOSEN.remove(player.getUUID());
        else CHOSEN.put(player.getUUID(), team);
        ClickDetector.Clicker who = new ClickDetector.Clicker(player.getUUID().toString());
        if (before != null) before.playerRemoved.fire(who);
        if (team != null) team.playerAdded.fire(who);
        mirror(player);
    }

    static void left(ServerPlayer player) {
        Team before = of(player);
        CHOSEN.remove(player.getUUID());
        if (before != null) before.playerRemoved.fire(new ClickDetector.Clicker(player.getUUID().toString()));
    }

    static void tick(MinecraftServer server) {
        InstanceTree tree = ServerScene.tree();
        if (tree == null) return;
        CHOSEN.values().removeIf(team -> !team.isAlive());
        List<SpawnLocation> pads = new ArrayList<>();
        for (SpawnLocation pad : tree.ofClass(Classes.SPAWN_LOCATION)) {
            if (pad.allowTeamChangeOnTouch && !pad.neutral && pad.enabled) pads.add(pad);
        }
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            Character body = Physics.bodies().of(player, tree);
            if (body != null && !pads.isEmpty()) {
                Vector3 feet = Transforms.world(body).position();
                for (SpawnLocation pad : pads) {
                    if (!standingOn(pad, feet)) continue;
                    Team match = teamColored(tree, pad);
                    if (match != null) set(player, match);
                    break;
                }
            }
            mirror(player);
        }
    }

    static boolean allowed(SpawnLocation pad, @Nullable Team team) {
        return pad.neutral || team != null && pad.teamColor.equals(team.teamColor);
    }

    private static void mirror(ServerPlayer player) {
        InstanceTree tree = ServerScene.tree();
        Character body = tree == null ? null : Physics.bodies().of(player, tree);
        Team team = of(player);
        if (body != null && body.team != team) Instances.setObj(body, TEAM, team);
    }

    private static int size(Team team) {
        int count = 0;
        for (Team chosen : CHOSEN.values()) {
            if (chosen == team) count++;
        }
        return count;
    }

    public static Collection<UUID> members(Team team) {
        List<UUID> members = new ArrayList<>();
        for (Map.Entry<UUID, Team> entry : CHOSEN.entrySet()) {
            if (entry.getValue() == team) members.add(entry.getKey());
        }
        return members;
    }

    private static @Nullable Team teamColored(InstanceTree tree, SpawnLocation pad) {
        for (Team team : tree.ofClass(Classes.TEAM)) {
            if (team.teamColor.equals(pad.teamColor)) return team;
        }
        return null;
    }

    private static boolean standingOn(SpawnLocation pad, Vector3 feet) {
        CFrame frame = Transforms.world(pad);
        Vector3 local = frame.pointToObject(feet);
        return Math.abs(local.x()) <= pad.size.x() * 0.5 && Math.abs(local.z()) <= pad.size.z() * 0.5
                && local.y() >= pad.size.y() * 0.5 - 0.1 && local.y() <= pad.size.y() * 0.5 + PAD_HEIGHT;
    }
}
