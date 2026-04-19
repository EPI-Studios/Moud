package com.moud.client.fabric.mixin;

import com.moud.client.fabric.mixin.accessor.EntityGroundAccessor;
import com.moud.client.fabric.physics.ClientPhysicsWorld;
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
    private static final double GROUND_PROBE = 0.1;

    @Inject(method = "tickMovement", at = @At("RETURN"))
    private void moud$drainStaticGravity(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        if ((Object) this != client.player) return;
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime == null || !runtime.isActive() || runtime.isCharacterBodyDriving()) return;
        ClientPhysicsWorld physics = ClientPhysicsWorld.get();
        if (!physics.isAvailable()) return;
        physics.syncSceneIfNeeded();

        Entity self = (Entity) (Object) this;
        if (self.isOnGround()) return;
        Box bb = self.getBoundingBox();
        double cx = (bb.minX + bb.maxX) * 0.5;
        double cz = (bb.minZ + bb.maxZ) * 0.5;
        var hitY = physics.raycastFloor(cx, bb.minY + 0.05, cz, GROUND_PROBE);
        if (hitY.isPresent()) {
            Vec3d v = self.getVelocity();
            if (v.y < 0) self.setVelocity(v.x, 0, v.z);
            ((EntityGroundAccessor) self).setOnGround(true);
        }
    }
}
