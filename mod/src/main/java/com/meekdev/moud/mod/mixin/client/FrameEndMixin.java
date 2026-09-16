package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.adapter.ui.Windows;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class FrameEndMixin {

    @Inject(method = "renderFrame", at = @At("TAIL"))
    private void moud$extraWindows(boolean advanceGameTime, CallbackInfo ci) {
        Windows.frame();
    }
}
