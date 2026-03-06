package com.moud.client.fabric.mixin;

import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public final class GameRendererMixin {
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void moud$bobView(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime != null && runtime.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void moud$tiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime != null && runtime.isActive()) {
            ci.cancel();
        }
    }
}

