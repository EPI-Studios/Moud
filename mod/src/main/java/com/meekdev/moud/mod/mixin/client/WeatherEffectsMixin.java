package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.adapter.render.WeatherView;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
abstract class WeatherEffectsMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void moud$ownPrecipitation(Vec3 camera, WeatherRenderState state, CallbackInfo ci) {
        if (WeatherView.weather() != null) ci.cancel();
    }

    @Inject(method = "tickRainParticles", at = @At("HEAD"), cancellable = true)
    private void moud$ownRainSounds(ClientLevel level, Camera camera, int ticks, ParticleStatus particles, int radius,
                                    CallbackInfo ci) {
        if (WeatherView.weather() != null) ci.cancel();
    }
}
