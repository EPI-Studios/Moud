package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.adapter.ui.Ui;
import com.meekdev.moud.mod.client.input.Devices;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class TypingMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void moud$textBox(long handle, int action, KeyEvent event, CallbackInfo ci) {
        boolean typing = Ui.typing(action, event.key(), event.scancode(), event.modifiers());
        boolean main = handle == Minecraft.getInstance().getWindow().handle();
        if (main && Devices.key(event.key(), action, typing) || typing) ci.cancel();
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void moud$textBoxCharacter(long handle, CharacterEvent event, CallbackInfo ci) {
        if (Ui.typed(event.codepoint())) ci.cancel();
    }
}
