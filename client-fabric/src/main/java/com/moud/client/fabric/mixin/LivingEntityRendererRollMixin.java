package com.moud.client.fabric.mixin;

import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererRollMixin {

    @Unique
    private boolean moud$pushedRoll = false;

    @Inject(method = "render", at = @At("HEAD"))
    private <T extends LivingEntity> void moud$pushRoll(
            T entity,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci) {
        moud$pushedRoll = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.player != entity) return;
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime == null || !runtime.isActive()) return;
        float roll = runtime.playerRoll();
        if (Math.abs(roll) < 1e-4f) return;
        matrices.push();
        matrices.multiply(new Quaternionf().rotateZ((float) Math.toRadians(roll)));
        moud$pushedRoll = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private <T extends LivingEntity> void moud$popRoll(
            T entity,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci) {
        if (moud$pushedRoll) {
            matrices.pop();
            moud$pushedRoll = false;
        }
    }
}
