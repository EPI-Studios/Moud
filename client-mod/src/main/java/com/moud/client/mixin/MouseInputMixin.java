package com.moud.client.mixin;

import com.moud.client.MoudClientMod;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseInputMixin {

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void interceptMouseInput(CallbackInfo ci) {
        if (MoudClientMod.isSmoothCameraActive()) {
            // need debugs
            ci.cancel();
        }
    }
}