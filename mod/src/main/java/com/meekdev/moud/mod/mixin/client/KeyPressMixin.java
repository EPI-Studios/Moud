package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.client.input.Devices;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyPressMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void moud$scripts(long handle, int action, KeyEvent event, CallbackInfo ci) {
        if (handle == Minecraft.getInstance().getWindow().handle() && Devices.key(event.key(), action)) ci.cancel();
    }
}
