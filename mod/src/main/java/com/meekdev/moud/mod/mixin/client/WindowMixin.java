package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.client.WindowApi;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
abstract class WindowMixin {

    @Inject(method = "shouldClose", at = @At("RETURN"), cancellable = true)
    private void moud$closing(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && WindowApi.INSTANCE.holdClose()) cir.setReturnValue(false);
    }
}
