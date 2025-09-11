package com.moud.client.mixin;

import com.moud.client.api.service.ClientAPIService;
import com.moud.client.ui.UIInputManager;
import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Inject(method = "onKey(JIIII)V", at = @At("HEAD"), cancellable = true)
    private void moud_onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (key == GLFW.GLFW_KEY_P && action == GLFW.GLFW_PRESS) {
            // to remove, it needs to be something that can be bound in client script
            if (ClientAPIService.INSTANCE != null) {
                ClientAPIService.INSTANCE.cursor.toggle();
                ci.cancel();
                return;
            }
        }

        if (UIInputManager.handleGlobalKeyPress(key, action)) {
            ci.cancel();
        }
    }
    @Inject(method = "onChar(JII)V", at = @At("HEAD"), cancellable = true)
    private void moud_onChar(long window, int codePoint, int modifiers, CallbackInfo ci) {
        if (UIInputManager.handleGlobalCharTyped((char) codePoint)) {
            ci.cancel();
        }
    }
}