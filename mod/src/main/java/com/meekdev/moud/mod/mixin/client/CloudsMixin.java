package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.core.render.Clouds;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.render.Environment;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
abstract class CloudsMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(CallbackInfo ci) {
        Clouds clouds = Environment.clouds();
        if (clouds != null) {
            if (!clouds.enabled || Environment.cloudCover() <= 0 || clouds.density <= 0) ci.cancel();
            return;
        }
        if (!MoudMod.features().isOn(Feature.CLOUDS)) ci.cancel();
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int moud$tint(int color) {
        return Environment.cloudColor(color);
    }
}
