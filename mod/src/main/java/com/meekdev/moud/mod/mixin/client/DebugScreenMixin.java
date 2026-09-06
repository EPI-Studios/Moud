package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// suppresses debugScreen
// f3 is read straight off the overlay, nothing about it goes through the hud extract pass
@Mixin(DebugScreenOverlay.class)
abstract class DebugScreenMixin {

    @Inject(method = "showDebugScreen", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(CallbackInfoReturnable<Boolean> cir) {
        if (!MoudMod.features().isOn(Feature.DEBUG_SCREEN)) cir.setReturnValue(false);
    }
}
