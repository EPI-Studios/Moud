package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.adapter.ui.Ui;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class ClickMixin {

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void moud$interface(long handle, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (Ui.click(button.button(), action == 1)) ci.cancel();
    }
}
