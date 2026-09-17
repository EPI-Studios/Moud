package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.character.HumanoidState;
import com.meekdev.moud.core.character.Humanoids;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.adapter.physics.Physics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class HumanoidStates {

    private static final PropertyDef MOVE_DIRECTION = Classes.HUMANOID.property("moveDirection");
    private static final PropertyDef FLOOR = Classes.HUMANOID.property("floorMaterial");
    private static final PropertyDef SIT = Classes.HUMANOID.property("sit");
    private static final double TICKS = 20;
    private static final double MOVING = 0.1;
    private static final double RISING = 0.05;
    private static final double FEET = 0.05;
    private static final String AIR = "air";

    private static final Map<UUID, Vector3> LAST = new HashMap<>();

    private HumanoidStates() {}

    public static void tick(MinecraftServer server, @Nullable InstanceTree tree) {
        if (tree == null) return;
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            Character body = Physics.bodies().of(player, tree);
            Humanoid living = body == null ? null : Rig.humanoid(body);
            if (living == null) {
                LAST.remove(player.getUUID());
                continue;
            }
            Vector3 at = new Vector3(player.getX(), player.getY(), player.getZ());
            Vector3 before = LAST.put(player.getUUID(), at);
            Vector3 velocity = before == null ? Vector3.ZERO : at.sub(before).mul(TICKS);
            player(player, living, velocity);
        }
        for (Character body : tree.ofClass(Classes.CHARACTER)) {
            Humanoid living = Rig.humanoid(body);
            if (living == null || !body.isAlive()) continue;
            ServerLevel level = Physics.level();
            if (level == null) continue;
            String floor = floor(level, Transforms.world(body).position(), onGround(body, living));
            if (!floor.equals(living.floorMaterial)) Instances.setObj(living, FLOOR, floor);
        }
    }

    private static boolean onGround(Character body, Humanoid living) {
        return living.state != HumanoidState.JUMPING && living.state != HumanoidState.FALLING
                && living.state != HumanoidState.SWIMMING && living.state != HumanoidState.FLYING;
    }

    private static void player(ServerPlayer player, Humanoid living, Vector3 velocity) {
        double speed = Math.hypot(velocity.x(), velocity.z());
        Vector3 direction = speed > MOVING ? new Vector3(velocity.x() / speed, 0, velocity.z() / speed) : Vector3.ZERO;
        if (direction.sub(living.moveDirection).lengthSq() > 1.0e-3) Instances.setObj(living, MOVE_DIRECTION, direction);
        if (living.state == HumanoidState.DEAD) {
            Spawning.humanoid(player, true, true);
            return;
        }
        if (living.sit && (velocity.y() > RISING && !player.onGround() || !living.stateEnabled(HumanoidState.SEATED))) {
            Instances.setBool(living, SIT, false);
        }
        HumanoidState next;
        if (living.platformStand) next = HumanoidState.PLATFORM_STANDING;
        else if (living.sit || player.isPassenger()) next = HumanoidState.SEATED;
        else if (player.getAbilities().flying) next = HumanoidState.FLYING;
        else if (player.onClimbable() && !player.onGround()) next = HumanoidState.CLIMBING;
        else if (player.isInWater() && !player.onGround()) next = HumanoidState.SWIMMING;
        else if (!player.onGround()) next = velocity.y() > RISING ? HumanoidState.JUMPING : HumanoidState.FALLING;
        else next = speed > MOVING ? HumanoidState.RUNNING : HumanoidState.STANDING;
        if (!living.stateEnabled(next) && next != HumanoidState.RUNNING && next != HumanoidState.JUMPING) next = HumanoidState.STANDING;
        Humanoids.observe(living, next, speed);
        boolean pinned = living.platformStand || living.sit;
        Spawning.humanoid(player, !pinned && living.stateEnabled(HumanoidState.RUNNING), !living.platformStand && living.stateEnabled(HumanoidState.JUMPING));
    }

    private static String floor(ServerLevel level, Vector3 feet, boolean grounded) {
        if (!grounded) return AIR;
        BlockPos below = BlockPos.containing(feet.x(), feet.y() - FEET, feet.z());
        BlockState state = level.getBlockState(below);
        if (state.isAir()) return AIR;
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    public static void left(ServerPlayer player) {
        LAST.remove(player.getUUID());
    }
}
