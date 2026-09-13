package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
abstract class CloudsMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.CLOUDS)) ci.cancel();
    }
}
