package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
abstract class SoundMixin {

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (refuse(sound)) cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
    }

    @Inject(method = "playDelayed", at = @At("HEAD"), cancellable = true)
    private void moud$suppressDelayed(SoundInstance sound, int delay, CallbackInfo ci) {
        if (refuse(sound)) ci.cancel();
    }

    private static boolean refuse(SoundInstance sound) {
        Feature feature = sound.getSource() == SoundSource.MUSIC ? Feature.VANILLA_MUSIC : Feature.VANILLA_SOUNDS;
        return !MoudMod.features().isOn(feature);
    }
}
