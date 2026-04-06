package com.moud.client.fabric.mixin;

import com.moud.client.fabric.player.PlayerBodyAttachmentCache;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererPlayerAttachMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private <T extends LivingEntity> void moud$cachePlayerAttachments(
            T entity,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci) {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime == null || !runtime.isActive()) {
            return;
        }
        if (!(entity instanceof AbstractClientPlayerEntity player)) {
            return;
        }
        PlayerBodyAttachmentCache.update(player, tickDelta);
    }
}
