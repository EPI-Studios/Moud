package com.moud.client.mixin;

import com.moud.client.api.service.ClientAPIService;
import com.moud.client.ui.UIInputHandler;
import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    private static UIInputHandler uiInputHandler = new UIInputHandler();

    @Inject(method = "onKey(JIIII)V", at = @At("HEAD"), cancellable = true)
    private void moud_onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (key == GLFW.GLFW_KEY_P && action == GLFW.GLFW_PRESS) {
            if (ClientAPIService.INSTANCE != null) {
                ClientAPIService.INSTANCE.cursor.toggle();
                ci.cancel();
                return;
            }
        }

        if (uiInputHandler.handleKeyPress(key, action)) {
            ci.cancel();
            return;
        }

        if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.input != null) {
            if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_RELEASE) {
                boolean handled = ClientAPIService.INSTANCE.input.handleKeyEvent(key, action);
                if (handled) {
                    ci.cancel();
                    return;
                }
            }
        }
    }

    @Inject(method = "onChar(JII)V", at = @At("HEAD"), cancellable = true)
    private void moud_onChar(long window, int codePoint, int modifiers, CallbackInfo ci) {
        if (uiInputHandler.handleCharTyped((char) codePoint)) {
            ci.cancel();
        }
    }

    public static UIInputHandler getUIInputHandler() {
        return uiInputHandler;
    }
}