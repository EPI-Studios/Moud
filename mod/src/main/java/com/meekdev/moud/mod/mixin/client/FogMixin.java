package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.render.Environment;
import com.meekdev.moud.mod.features.Feature;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
abstract class FogMixin {

    @Shadow
    private static boolean fogEnabled;

    @Inject(method = "getBuffer", at = @At("HEAD"))
    private void moud$suppress(FogRenderer.FogMode mode, CallbackInfoReturnable<GpuBufferSlice> cir) {
        fogEnabled = MoudMod.features().isOn(Feature.FOG) || Environment.fogged();
    }

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void moud$atmosphere(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker,
                                 float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        Environment.fog(cir.getReturnValue(), camera.forwardVector(), renderDistanceInChunks * 16.0);
    }
}
