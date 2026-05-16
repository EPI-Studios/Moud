package com.moud.client.fabric.mixin;

import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntityRenderer.class, priority = 900)
public abstract class LivingEntityRendererLocalPlayerHideMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <T extends LivingEntity> void moud$hideLocalPlayerForSceneCamera(
            T entity, float yaw, float tickDelta,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || entity != mc.player) return;
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime == null || !runtime.isActive() || !runtime.isUsingSceneCamera()) return;
        ci.cancel();
    }
}
