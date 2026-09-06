package com.meekdev.moud.mod.mixin.world;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// suppresses dayNightCycle
@Mixin(ServerLevel.class)
abstract class TimeMixin {

    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.DAY_NIGHT_CYCLE)) ci.cancel();
    }
}
