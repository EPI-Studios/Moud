package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.client.GameState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class PauseMixin {

    @Inject(method = "isPaused", at = @At("HEAD"), cancellable = true)
    private void moud$scriptPause(CallbackInfoReturnable<Boolean> cir) {
        if (GameState.INSTANCE.pausing()) cir.setReturnValue(true);
    }

    @Inject(method = "pauseIfInactive", at = @At("HEAD"))
    private void moud$focusLostBegin(CallbackInfo ci) {
        GameState.INSTANCE.focusLost(true);
    }

    @Inject(method = "pauseIfInactive", at = @At("RETURN"))
    private void moud$focusLostEnd(CallbackInfo ci) {
        GameState.INSTANCE.focusLost(false);
    }
}
