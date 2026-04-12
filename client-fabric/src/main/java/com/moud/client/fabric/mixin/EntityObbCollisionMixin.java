package com.moud.client.fabric.mixin;

import com.moud.client.fabric.mixin.accessor.EntityGroundAccessor;
import com.moud.client.fabric.physics.CollisionShape;
import com.moud.client.fabric.physics.CsgBoxCollisionCache;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityObbCollisionMixin {

    private static final int PLAYER_COLLISION_LAYER = 1;
    private static final int PLAYER_COLLISION_MASK  = 0x7FFF_FFFF;
    private static final int SOLVER_ITERATIONS      = 3;
    private static final double MAX_MTV_SQ          = 4.0;

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, index = 2)
    private Vec3d moud$deflectAgainstObbs(Vec3d movement) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return movement;
        if ((Object) this != client.player) return movement;

        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime == null || !runtime.isActive()) return movement;

        CollisionShape[] shapes = CsgBoxCollisionCache.get();
        if (shapes.length == 0) return movement;

        Entity self = (Entity) (Object) this;
        Box bb = self.getBoundingBox();

        double origCx = (bb.minX + bb.maxX) * 0.5;
        double origCy = (bb.minY + bb.maxY) * 0.5;
        double origCz = (bb.minZ + bb.maxZ) * 0.5;
        double bbHw   = (bb.maxX - bb.minX) * 0.5;
        double bbHh   = (bb.maxY - bb.minY) * 0.5;
        double bbHd   = (bb.maxZ - bb.minZ) * 0.5;

        double cx = origCx;
        double cy = origCy + movement.y;
        double cz = origCz;
        boolean floorHit   = false;
        boolean ceilingHit = false;

        for (int iter = 0; iter < SOLVER_ITERATIONS; iter++) {
            boolean anyHit = false;
            for (CollisionShape shape : shapes) {
                if (!canCollide(PLAYER_COLLISION_LAYER, PLAYER_COLLISION_MASK, shape.layerBits(), shape.maskBits())) continue;
                if (!broadPhase(cx, cy, cz, bbHw, bbHh, bbHd, shape)) continue;
                double[] mtv = shape.computeMtv(cx, cy, cz, bbHw, bbHh, bbHd);
                if (mtv == null) continue;
                if (mtv[0]*mtv[0] + mtv[1]*mtv[1] + mtv[2]*mtv[2] > MAX_MTV_SQ) continue;
                cy += mtv[1];
                if (mtv[1] >  1e-7) floorHit   = true;
                if (mtv[1] < -1e-7) ceilingHit = true;
                anyHit = true;
            }
            if (!anyHit) break;
        }

        if (floorHit) {
            Vec3d vel = self.getVelocity();
            if (vel.y < 0) self.setVelocity(vel.x, 0, vel.z);
            ((EntityGroundAccessor) self).setOnGround(true);
        }
        if (ceilingHit) {
            Vec3d vel = self.getVelocity();
            if (vel.y > 0) self.setVelocity(vel.x, 0, vel.z);
        }

        cx = origCx + movement.x;
        cz = origCz + movement.z;

        for (int iter = 0; iter < SOLVER_ITERATIONS; iter++) {
            boolean anyHit = false;
            for (CollisionShape shape : shapes) {
                if (!canCollide(PLAYER_COLLISION_LAYER, PLAYER_COLLISION_MASK, shape.layerBits(), shape.maskBits())) continue;
                if (!broadPhase(cx, cy, cz, bbHw, bbHh, bbHd, shape)) continue;
                double[] mtv = shape.computeMtv(cx, cy, cz, bbHw, bbHh, bbHd);
                if (mtv == null) continue;
                if (mtv[0]*mtv[0] + mtv[1]*mtv[1] + mtv[2]*mtv[2] > MAX_MTV_SQ) continue;
                cx += mtv[0];
                cz += mtv[2];
                anyHit = true;
            }
            if (!anyHit) break;
        }

        double newX = cx - origCx;
        double newY = cy - origCy;
        double newZ = cz - origCz;

        if (newX == movement.x && newY == movement.y && newZ == movement.z) return movement;
        return new Vec3d(newX, newY, newZ);
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void moud$reapplyObbGround(net.minecraft.entity.MovementType type, Vec3d movement, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        if ((Object) this != client.player) return;

        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime == null || !runtime.isActive()) return;

        Entity self = (Entity) (Object) this;
        if (self.isOnGround()) return;

        CollisionShape[] shapes = CsgBoxCollisionCache.get();
        if (shapes.length == 0) return;

        Box bb = self.getBoundingBox();
        double cx  = (bb.minX + bb.maxX) * 0.5;
        double cy  = (bb.minY + bb.maxY) * 0.5;
        double cz  = (bb.minZ + bb.maxZ) * 0.5;
        double bbHw = (bb.maxX - bb.minX) * 0.5;
        double bbHh = (bb.maxY - bb.minY) * 0.5;
        double bbHd = (bb.maxZ - bb.minZ) * 0.5;
        double probe = 0.05;

        for (CollisionShape shape : shapes) {
            if (!canCollide(PLAYER_COLLISION_LAYER, PLAYER_COLLISION_MASK, shape.layerBits(), shape.maskBits())) continue;
            if (!broadPhase(cx, cy - probe, cz, bbHw, bbHh + probe, bbHd, shape)) continue;
            double[] mtv = shape.computeMtv(cx, cy - probe, cz, bbHw, bbHh + probe, bbHd);
            if (mtv == null) continue;
            if (mtv[1] <= 1e-4) continue;
            Vec3d vel = self.getVelocity();
            if (vel.y < 0) self.setVelocity(vel.x, 0, vel.z);
            ((EntityGroundAccessor) self).setOnGround(true);
            return;
        }
    }

    private static boolean broadPhase(
            double cx, double cy, double cz,
            double hw, double hh, double hd,
            CollisionShape shape) {
        return shape.worldAabb().intersects(
                cx - hw, cy - hh, cz - hd,
                cx + hw, cy + hh, cz + hd);
    }

    private static boolean canCollide(int layerA, int maskA, int layerB, int maskB) {
        return (layerA & maskB) != 0 && (layerB & maskA) != 0;
    }
}
