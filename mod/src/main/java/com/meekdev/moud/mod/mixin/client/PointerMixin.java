package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.client.EditMode;
import com.meekdev.moud.mod.client.input.Input;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class PointerMixin {

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void moud$keepPointerFree(CallbackInfo ci) {
        if (Input.scriptWantsPointer() && !EditMode.editing() && Minecraft.getInstance().screen == null) ci.cancel();
    }
}
