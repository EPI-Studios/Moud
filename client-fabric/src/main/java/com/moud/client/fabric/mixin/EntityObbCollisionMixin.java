package com.moud.client.fabric.mixin;

import com.moud.client.fabric.physics.CsgBoxCollisionCache;
import com.moud.client.fabric.physics.ObbCollisionShape;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityObbCollisionMixin {

    private static final int PLAYER_COLLISION_LAYER = 1;
    private static final int PLAYER_COLLISION_MASK = 0x7FFF_FFFF;
    private static final int SOLVER_ITERATIONS = 3;
    private static final double MAX_MTV_SQ = 4.0;

    @Inject(method = "adjustMovementForCollisions", at = @At("RETURN"), cancellable = true)
    private void moud$applyObbCollisions(Vec3d movement, CallbackInfoReturnable<Vec3d> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        if ((Object) this != client.player) return;

        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime == null || !runtime.isActive()) return;

        ObbCollisionShape[] obbs = CsgBoxCollisionCache.get();
        if (obbs.length == 0) return;

        Vec3d adjusted = cir.getReturnValue();
        if (adjusted == null) return;
        Entity self = (Entity) (Object) this;
        Box bb = self.getBoundingBox();
        double bbCx = (bb.minX + bb.maxX) * 0.5;
        double bbCy = (bb.minY + bb.maxY) * 0.5;
        double bbCz = (bb.minZ + bb.maxZ) * 0.5;
        double bbHw = (bb.maxX - bb.minX) * 0.5;
        double bbHh = (bb.maxY - bb.minY) * 0.5;
        double bbHd = (bb.maxZ - bb.minZ) * 0.5;

        double cx = bbCx + adjusted.x;
        double cy = bbCy + adjusted.y;
        double cz = bbCz + adjusted.z;

        double totalMtvX = 0, totalMtvY = 0, totalMtvZ = 0;
        boolean modified = false;

        for (int iter = 0; iter < SOLVER_ITERATIONS; iter++) {
            boolean anyHit = false;
            for (ObbCollisionShape obb : obbs) {
                if (!canCollide(PLAYER_COLLISION_LAYER, PLAYER_COLLISION_MASK, obb.layerBits(), obb.maskBits())) {
                    continue;
                }
                if (!obb.worldAabb().intersects(
                        cx - bbHw, cy - bbHh, cz - bbHd,
                        cx + bbHw, cy + bbHh, cz + bbHd)) {
                    continue;
                }
                double[] mtv = obb.computeMtv(cx, cy, cz, bbHw, bbHh, bbHd);
                if (mtv == null) continue;
                if (mtv[0]*mtv[0] + mtv[1]*mtv[1] + mtv[2]*mtv[2] > MAX_MTV_SQ) continue;
                cx += mtv[0]; cy += mtv[1]; cz += mtv[2];
                totalMtvX += mtv[0]; totalMtvY += mtv[1]; totalMtvZ += mtv[2];
                anyHit = true;
                modified = true;
            }
            if (!anyHit) break;
        }

        if (modified) {
            double lenSq = totalMtvX*totalMtvX + totalMtvY*totalMtvY + totalMtvZ*totalMtvZ;
            double vx = adjusted.x, vy = adjusted.y, vz = adjusted.z;
            if (lenSq > 1e-12) {
                double invLen = 1.0 / Math.sqrt(lenSq);
                double nx = totalMtvX * invLen;
                double ny = totalMtvY * invLen;
                double nz = totalMtvZ * invLen;
                double dot = vx * nx + vy * ny + vz * nz;
                if (dot < 0) {
                    vx -= dot * nx;
                    vy -= dot * ny;
                    vz -= dot * nz;
                }
            }
            cir.setReturnValue(new Vec3d(
                    cx - bbCx + (vx - adjusted.x),
                    cy - bbCy + (vy - adjusted.y),
                    cz - bbCz + (vz - adjusted.z)));
        }
    }

    private static boolean canCollide(int layerA, int maskA, int layerB, int maskB) {
        return (layerA & maskB) != 0 && (layerB & maskA) != 0;
    }
}
