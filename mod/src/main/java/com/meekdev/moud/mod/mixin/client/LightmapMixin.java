package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.adapter.render.Environment;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
abstract class LightmapMixin {

    @Inject(method = "extract", at = @At("RETURN"))
    private void moud$lighting(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
        if (!state.needsUpdate || Environment.lighting() == null) return;
        state.skyFactor = Environment.skyFactor(state.skyFactor);
        state.skyLightColor = Environment.skyLight(new Vector3f(state.skyLightColor));
        state.ambientColor = Environment.ambient(new Vector3f(state.ambientColor));
    }
}
