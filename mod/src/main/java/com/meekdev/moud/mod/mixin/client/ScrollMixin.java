package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.client.input.Devices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class ScrollMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void moud$scripts(long handle, double across, double amount, CallbackInfo ci) {
        if (handle == Minecraft.getInstance().getWindow().handle() && Devices.scroll(amount)) ci.cancel();
    }
}
