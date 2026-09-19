package com.meekdev.moud.mod.mixin.world;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import com.meekdev.moud.mod.server.ServerWeather;
import com.meekdev.moud.mod.server.WorldTime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class WeatherMixin {

    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void moud$suppressCycle(CallbackInfo ci) {
        if (WorldTime.driven() || !MoudMod.features().isOn(Feature.WEATHER)) ci.cancel();
    }

    @Inject(method = "tickThunder", at = @At("HEAD"), cancellable = true)
    private void moud$suppressThunder(LevelChunk chunk, CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.WEATHER) || ServerWeather.weather() != null) ci.cancel();
    }
}
