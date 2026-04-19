package com.moud.client.fabric.mixin;

import com.moud.client.fabric.mixin.accessor.EntityGroundAccessor;
import com.moud.client.fabric.physics.ClientPhysicsWorld;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class EntityObbCollisionMixin {
    private static final int SOLVER_ITERATIONS = 3;

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
        double cx = (bb.minX + bb.maxX) * 0.5 + movement.x;
        double cy = (bb.minY + bb.maxY) * 0.5 + movement.y;
        double cz = (bb.minZ + bb.maxZ) * 0.5 + movement.z;
        double originCx = cx - movement.x, originCy = cy - movement.y, originCz = cz - movement.z;

        boolean floorHit = false;
        boolean ceilingHit = false;
        double probeRadius = Math.max(0.05, Math.min(hw, Math.min(hh, hd)) * 0.5);
        for (int iter = 0; iter < SOLVER_ITERATIONS; iter++) {
            boolean any = false;
            if (physics.overlapSphere(cx, cy - hh + probeRadius, cz, probeRadius)) {
                cy += probeRadius * 0.25;
                floorHit = true;
                any = true;
            }
            if (physics.overlapSphere(cx, cy + hh - probeRadius, cz, probeRadius)) {
                cy -= probeRadius * 0.25;
                ceilingHit = true;
                any = true;
            }
            if (!any) break;
        }

        if (floorHit) {
            Vec3d v = self.getVelocity();
            if (v.y < 0) self.setVelocity(v.x, 0, v.z);
            ((EntityGroundAccessor) self).setOnGround(true);
        }
        if (ceilingHit) {
            Vec3d v = self.getVelocity();
            if (v.y > 0) self.setVelocity(v.x, 0, v.z);
        }

        return new Vec3d(cx - originCx, cy - originCy, cz - originCz);
    }
}
