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

        double newCx = bbCx + adjusted.x;
        double newCy = bbCy + adjusted.y;
        double newCz = bbCz + adjusted.z;

        boolean modified = false;
        for (ObbCollisionShape obb : obbs) {
            if (!canCollide(PLAYER_COLLISION_LAYER, PLAYER_COLLISION_MASK, obb.layerBits(), obb.maskBits())) {
                continue;
            }
            double[] mtv = obb.computeMtv(newCx, newCy, newCz, bbHw, bbHh, bbHd);
            if (mtv != null) {
                newCx += mtv[0];
                newCy += mtv[1];
                newCz += mtv[2];
                modified = true;
            }
        }

        if (modified) {
            // Convert back to movement delta
            cir.setReturnValue(new Vec3d(newCx - bbCx, newCy - bbCy, newCz - bbCz));
        }
    }

    private static boolean canCollide(int layerA, int maskA, int layerB, int maskB) {
        return (layerA & maskB) != 0 && (layerB & maskA) != 0;
    }
}
