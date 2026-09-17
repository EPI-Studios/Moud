package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.adapter.ui.Ui;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class WheelMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void moud$interface(long handle, double horizontal, double vertical, CallbackInfo ci) {
        if (Ui.scroll(vertical)) ci.cancel();
    }
}
