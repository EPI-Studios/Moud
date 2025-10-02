package com.moud.client.mixin;

import com.moud.client.api.service.ClientAPIService;
import com.moud.client.camera.CameraManager;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        CameraManager.tick(1.0f);
    }

    @Inject(method = "onResolutionChanged", at = @At("TAIL"))
    private void moud_onResolutionChanged(CallbackInfo info) {
        if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.ui != null) {
            ClientAPIService.INSTANCE.ui.triggerResizeEvent();
        }
    }
}