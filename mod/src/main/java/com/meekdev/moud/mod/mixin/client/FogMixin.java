package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// suppresses fog
// the renderer already hands back an empty buffer when its own flag is down, so the switch only has
// to hold that flag rather than build a second empty buffer of its own
@Mixin(FogRenderer.class)
abstract class FogMixin {

    @Shadow
    private static boolean fogEnabled;

    @Inject(method = "getBuffer", at = @At("HEAD"))
    private void moud$suppress(FogRenderer.FogMode mode, CallbackInfoReturnable<GpuBufferSlice> cir) {
        fogEnabled = MoudMod.features().isOn(Feature.FOG);
    }
}
