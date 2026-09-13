package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.physics.MovementProfile;
import com.meekdev.bkun.physics.Physics;
import com.meekdev.bkun.sublevel.SubLevelEntity;
import com.meekdev.bkun.sublevel.SubLevelTracking;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.CollisionGroups;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.net.replicate.Change;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

public final class Characters {

    private static final double TICKS = 20.0;

    private static final PropertyDef CFRAME = Classes.CHARACTER.property("cframe");
    private static final PropertyDef OWNER = Classes.CHARACTER.property("owner");
    private static final PropertyDef COLLISION_GROUP = Classes.CHARACTER.property("collisionGroup");

    private final Map<UUID, Integer> bound = new HashMap<>();

    private final Colliders boxes;

    Characters(Colliders boxes) {
        this.boxes = boxes;
    }

    private CollisionGroups groups() {
        return boxes.groups();
    }

    public static MovementProfile currentProfile(LivingEntity entity) {
        return Physics.getProfile(entity).orElse(null);
    }

    public MovementProfile profileOf(Character character) {
        Humanoid living = Rig.humanoid(character);
        if (living == null) return MovementProfile.builder().build();
        return MovementProfile.builder()
                .gravityScale(living.gravityScale)
                .maxGroundSpeed(living.walkSpeed / TICKS)
                .sprintMultiplier(living.sprintMultiplier)
                .sneakMultiplier(living.sneakMultiplier)
                .maxAirSpeed(living.airSpeed / TICKS)
                .groundAcceleration(living.groundAcceleration / (TICKS * TICKS))
                .groundDeceleration(living.groundDeceleration / (TICKS * TICKS))
                .airAcceleration(living.airAcceleration / (TICKS * TICKS))
                .slideAcceleration(living.slideAcceleration / (TICKS * TICKS))
                .jumpPower(living.jumpPower / TICKS)
                .airDrag(Math.pow(living.airDrag, 1.0 / TICKS))
                .fallDrag(Math.pow(living.fallDrag, 1.0 / TICKS))
                .stepHeight(living.stepHeight)
                .slideThresholdDegrees(living.slopeLimit)
                .coyoteTicks((int) Math.round(living.coyoteTime * TICKS))
                .jumpBufferTicks((int) Math.round(living.jumpBuffer * TICKS))
                .followSlopes(living.followSlopes)
                .moverShape(character.radius, character.height)
                .collisionFilter(groups().category(character.collisionGroup),
                        groups().mask(character.collisionGroup))
                .build();
    }

    public static void place(Character character, Vector3 position, double yawDegrees) {
        double yaw = Math.PI - Math.toRadians(yawDegrees);
        Instances.setObj(character, CFRAME, Transforms.localFor(character,
                new CFrame(position, Quat.euler(0, yaw, 0))));
    }

    public void follow(MinecraftServer server, @Nullable InstanceTree tree, SubLevels shapes) {
        if (tree == null) return;
        for (Map.Entry<UUID, Integer> entry : bound.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !(tree.byId(entry.getValue()) instanceof Character character)) {
                continue;
            }
            ride(character, player, tree, shapes);
            drive(character, player);
        }
    }

    public void ride(Character character, Entity player, InstanceTree tree, SubLevels shapes) {
        Instance want = tree.root();
        SubLevelEntity deck = SubLevelTracking.of(player);
        if (deck != null && !deck.isRemoved()) {
            int id = shapes.instanceOf(deck);
            if (tree.byId(id) instanceof Part part) want = part;
        }
        if (want == null || want == character || character.parent() == want) return;

        CFrame world = Transforms.world(character);
        Instances.reparent(character, want);
        Instances.setObj(character, CFRAME, Transforms.localFor(character, world));
    }

    public static void drive(Character character, Player player) {
        if (Math.abs(player.getBbWidth() - character.radius * 2) > 1e-4
                || Math.abs(player.getBbHeight() - character.height) > 1e-4) {
            player.refreshDimensions();
        }
        double heading = player.isSleeping() ? 180.0f - PlayerMirror.bedAngle(player) : player.yBodyRot;
        place(character, new Vector3(player.getX(), player.getY(), player.getZ()), heading);
        PlayerMirror.copy(character, player);
    }

    public void bind(ServerPlayer player, Character character) {
        bound.put(player.getUUID(), character.id());
        Instances.setObj(character, OWNER, player.getUUID().toString());
        Physics.setProfile(player, profileOf(character));
    }

    public void release(ServerPlayer player) {
        if (bound.remove(player.getUUID()) != null) Physics.clearProfile(player);
    }

    public @Nullable Character of(ServerPlayer player, @Nullable InstanceTree tree) {
        Integer id = bound.get(player.getUUID());
        if (id == null || tree == null) return null;
        return tree.byId(id) instanceof Character character ? character : null;
    }

    public void apply(InstanceTree source, Change change, MinecraftServer server) {
        if (boxes.groupsChanged()) {
            for (Map.Entry<UUID, Integer> entry : bound.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null && source.byId(entry.getValue()) instanceof Character character) {
                    Physics.setProfile(player, profileOf(character));
                }
            }
            return;
        }
        if (!(change instanceof Change.Wrote wrote)) return;
        Instance written = source.byId(wrote.id());
        if (written instanceof Character body && wrote.property() == COLLISION_GROUP.index()) {
            push(body, server);
        } else if (written instanceof Humanoid living && living.parent() instanceof Character body) {
            push(body, server);
        }
    }

    private void push(Character character, MinecraftServer server) {
        for (Map.Entry<UUID, Integer> entry : bound.entrySet()) {
            if (entry.getValue() != character.id()) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) Physics.setProfile(player, profileOf(character));
        }
    }
}
