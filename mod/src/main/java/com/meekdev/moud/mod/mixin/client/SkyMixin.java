package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
abstract class SkyMixin {

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void moud$suppress(ClientLevel level, float partial, Camera camera, SkyRenderState state, CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.SKY)) {
            state.skybox = DimensionType.Skybox.NONE;
            state.shouldRenderDarkDisc = false;
            state.skyColor = 0;
            state.sunriseAndSunsetColor = 0;
        }
        if (!MoudMod.features().isOn(Feature.STARS)) {
            state.starBrightness = 0.0f;
        }
    }
}
