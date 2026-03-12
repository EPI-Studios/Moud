package com.moud.client.fabric.mixin;

import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "onWindowFocusChanged", at = @At("HEAD"), cancellable = true)
    private void moud$onWindowFocusChanged(boolean focused, CallbackInfo ci) {
        if (focused) {
            return;
        }
        EditorContext ctx = EditorOverlayBus.get();
        if (ctx != null && ctx.isActive()) {
            ci.cancel();
            return;
        }
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime != null && runtime.isActive()) {
            ci.cancel();
        }
    }
}

