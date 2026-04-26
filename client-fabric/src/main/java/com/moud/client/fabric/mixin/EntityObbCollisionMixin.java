package com.moud.client.fabric.mixin;

import com.moud.client.fabric.mixin.accessor.EntityGroundAccessor;
import com.moud.client.fabric.physics.ClientPhysicsWorld;
import com.moud.client.fabric.physics.ClientPhysicsWorld.SweepHit;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Optional;

// godot-inspired move-and-slide against jolt
@Mixin(Entity.class)
public abstract class EntityObbCollisionMixin {
    private static final int MAX_SLIDES = 4;
    private static final double FLOOR_COS = Math.cos(Math.toRadians(46.0));
    private static final double FLOOR_SNAP = 0.6;
    private static final double SWEEP_EPSILON = 1.0e-3;
    private static final double STOP_EPSILON = 1.0e-6;

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, index = 2)
    private Vec3d moud$deflectAgainstJolt(Vec3d movement) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || (Object) this != client.player) return movement;
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime == null || !runtime.isActive() || runtime.isCharacterBodyDriving()) return movement;
        ClientPhysicsWorld physics = ClientPhysicsWorld.get();
        if (!physics.isAvailable()) return movement;
        physics.syncSceneIfNeeded();

        Entity self = (Entity) (Object) this;
        Box bb = self.getBoundingBox();
        double hw = (bb.maxX - bb.minX) * 0.5;
        double hh = (bb.maxY - bb.minY) * 0.5;
        double hd = (bb.maxZ - bb.minZ) * 0.5;
        double radius = Math.max(0.05, Math.min(hw, hd));
        double fullHeight = Math.max(radius * 2.0 + 1e-4, hh * 2.0);

        double originX = (bb.minX + bb.maxX) * 0.5;
        double originY = (bb.minY + bb.maxY) * 0.5;
        double originZ = (bb.minZ + bb.maxZ) * 0.5;
        Vec3d velocity = self.getVelocity();
        boolean wasOnGround = self.isOnGround();
        boolean velFacingUp = velocity.y > 1e-5 || movement.y > 1e-5;

        double cx = originX;
        double cy = originY;
        double cz = originZ;
        double mx = movement.x;
        double my = movement.y;
        double mz = movement.z;
        boolean grounded = false;
        boolean touchedCeiling = false;

        // depenetrate first, otherwise the slide loop self-cancels when starting inside a surface
        for (int d = 0; d < 3; d++) {
            Optional<SweepHit> probe = physics.sweepCapsule(
                    cx, cy - hh, cz, radius, fullHeight, 0.0, -1e-4, 0.0);
            if (probe.isEmpty() || probe.get().fraction() > 1e-6) break;
            SweepHit p = probe.get();
            double push = 0.02;
            cx += p.nx() * push;
            cy += p.ny() * push;
            cz += p.nz() * push;
        }

        for (int i = 0; i < MAX_SLIDES; i++) {
            double lenSq = mx * mx + my * my + mz * mz;
            if (lenSq < 1e-10) break;

            Optional<SweepHit> maybeHit = physics.sweepCapsule(
                    cx, cy - hh, cz, radius, fullHeight, mx, my, mz);

            if (maybeHit.isEmpty()) {
                cx += mx;
                cy += my;
                cz += mz;
                break;
            }

            SweepHit hit = maybeHit.get();
            double advance = Math.max(0.0, hit.fraction() - SWEEP_EPSILON);
            cx += mx * advance;
            cy += my * advance;
            cz += mz * advance;

            if (hit.ny() >= FLOOR_COS && !velFacingUp) {
                grounded = true;
            } else if (hit.ny() <= -FLOOR_COS) {
                touchedCeiling = true;
            }

            double remain = Math.max(0.0, 1.0 - hit.fraction());
            double remX = mx * remain;
            double remY = my * remain;
            double remZ = mz * remain;

            double nx = hit.nx();
            double ny = hit.ny();
            double nz = hit.nz();
            double into = remX * nx + remY * ny + remZ * nz;
            if (into > 0.0) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
                into = remX * nx + remY * ny + remZ * nz;
            }
            if (into < 0.0) {
                remX -= nx * into;
                remY -= ny * into;
                remZ -= nz * into;
            }

            mx = remX;
            my = remY;
            mz = remZ;
            if (mx * mx + my * my + mz * mz < STOP_EPSILON) break;
        }

        if (!grounded && wasOnGround && !velFacingUp) {
            Optional<SweepHit> snapHit = physics.sweepCapsule(
                    cx, cy - hh, cz, radius, fullHeight, 0.0, -FLOOR_SNAP, 0.0);
            if (snapHit.isPresent() && snapHit.get().ny() >= FLOOR_COS) {
                double snap = FLOOR_SNAP * Math.max(0.0, snapHit.get().fraction() - SWEEP_EPSILON);
                cy -= snap;
                grounded = true;
            }
        }

        if (grounded && velocity.y < 0.0) {
            self.setVelocity(velocity.x, 0.0, velocity.z);
        }
        if (touchedCeiling && velocity.y > 0.0) {
            self.setVelocity(velocity.x, 0.0, velocity.z);
        }

        double dx = cx - originX;
        double dy = cy - originY;
        double dz = cz - originZ;

        if (grounded && dy < 0.0) {
            dy = 0.0;
        }

        if (grounded) {
            ((EntityGroundAccessor) self).setOnGround(true);
        }

        return new Vec3d(dx, dy, dz);
    }
}
