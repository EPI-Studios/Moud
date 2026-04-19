package com.moud.client.fabric.mixin;

import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererRollMixin {

    @Unique
    private boolean moud$pushedTilt = false;

    @Inject(method = "render", at = @At("HEAD"))
    private <T extends LivingEntity> void moud$pushTilt(
            T entity,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci) {
        moud$pushedTilt = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.player != entity) return;
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime == null || !runtime.isActive()) return;
        float tiltDeg = runtime.playerRoll();
        if (Math.abs(tiltDeg) < 1e-4f) return;

        float bodyYaw = entity instanceof PlayerEntity pe ? pe.bodyYaw : yaw;
        float yawRad = (float) Math.toRadians(bodyYaw);
        float rx = (float) -Math.cos(yawRad);
        float rz = (float) -Math.sin(yawRad);
        Vector3f rightAxis = new Vector3f(rx, 0f, rz);

        matrices.push();
        matrices.multiply(new Quaternionf().fromAxisAngleRad(rightAxis, (float) Math.toRadians(tiltDeg)));
        moud$pushedTilt = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private <T extends LivingEntity> void moud$popTilt(
            T entity,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci) {
        if (moud$pushedTilt) {
            matrices.pop();
            moud$pushedTilt = false;
        }
    }
}
