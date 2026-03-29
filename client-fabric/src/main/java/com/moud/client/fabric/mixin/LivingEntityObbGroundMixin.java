package com.moud.client.fabric.mixin;

import com.moud.client.fabric.mixin.accessor.EntityGroundAccessor;
import com.moud.client.fabric.physics.CsgBoxCollisionCache;
import com.moud.client.fabric.physics.ObbCollisionShape;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityObbGroundMixin {

    private static final int PLAYER_COLLISION_LAYER = 1;
    private static final int PLAYER_COLLISION_MASK  = 0x7FFF_FFFF;
    private static final double GROUND_PROBE        = 0.05;

    @Inject(method = "tickMovement", at = @At("RETURN"))
    private void moud$drainObbGravity(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        if ((Object) this != client.player) return;

        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime == null || !runtime.isActive()) return;

        ObbCollisionShape[] obbs = CsgBoxCollisionCache.get();
        if (obbs.length == 0) return;

        Entity self = (Entity) (Object) this;
        Box bb = self.getBoundingBox();
        double bbHw = (bb.maxX - bb.minX) * 0.5;
        double bbHh = (bb.maxY - bb.minY) * 0.5;
        double bbHd = (bb.maxZ - bb.minZ) * 0.5;
        double cx   = (bb.minX + bb.maxX) * 0.5;
        double cy   = (bb.minY + bb.maxY) * 0.5;
        double cz   = (bb.minZ + bb.maxZ) * 0.5;

        for (ObbCollisionShape obb : obbs) {
            if (!canCollide(PLAYER_COLLISION_LAYER, PLAYER_COLLISION_MASK, obb.layerBits(), obb.maskBits())) continue;
            if (!obb.worldAabb().intersects(cx - bbHw, cy - bbHh - GROUND_PROBE, cz - bbHd,
                    cx + bbHw, cy + bbHh,                cz + bbHd)) continue;
            double[] mtv = obb.computeMtv(cx, cy - GROUND_PROBE, cz, bbHw, bbHh + GROUND_PROBE, bbHd);
            if (mtv == null) continue;
            Vec3d vel = self.getVelocity();
            if (vel.y < 0) self.setVelocity(vel.x, 0, vel.z);
            ((EntityGroundAccessor) self).setOnGround(true);
            return;
        }
    }

    private static boolean canCollide(int layerA, int maskA, int layerB, int maskB) {
        return (layerA & maskB) != 0 && (layerB & maskA) != 0;
    }
}