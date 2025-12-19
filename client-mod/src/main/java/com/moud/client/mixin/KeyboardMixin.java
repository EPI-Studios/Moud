package com.moud.client.mixin;

import com.moud.client.MoudClientMod;
import com.moud.client.api.service.ClientAPIService;
import com.moud.client.audio.VoiceChatController;
import com.moud.client.editor.EditorModeManager;
import com.moud.client.editor.ui.EditorImGuiLayer;
import com.moud.client.permissions.ClientPermissionState;
import com.moud.client.ui.UIInputManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Keyboard;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Inject(method = "onKey(JIIII)V", at = @At("HEAD"), cancellable = true)
    private void moud_onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        EditorModeManager editor = EditorModeManager.getInstance();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return;
        }

        if (EditorImGuiLayer.getInstance().handleKeyEvent(key, scancode, action, modifiers)) {
            ci.cancel();
            return;
        }

        if (key == GLFW.GLFW_KEY_F8 && action == GLFW.GLFW_PRESS && MoudClientMod.isOnMoudServer()) {
            if (!editor.isActive() && !ClientPermissionState.getInstance().canUseEditor()) {
                if (client != null && client.player != null) {
                    String message = ClientPermissionState.getInstance().isReceived()
                            ? "You do not have permission to use the editor."
                            : "Editor permissions not synced yet.";
                    client.player.sendMessage(Text.literal(message), true);
                }
                ci.cancel();
                return;
            }
            boolean enabled = editor.toggle();

            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal(enabled ? "Editor mode enabled" : "Editor mode disabled"), true);
            }
            ci.cancel();
            return;
        }

        if (editor.consumeKeyEvent(key, scancode, action, modifiers)) {
            ci.cancel();
            return;
        }

        if (key == GLFW.GLFW_KEY_P && action == GLFW.GLFW_PRESS) {
            if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.cursor != null) {
                ClientAPIService.INSTANCE.cursor.toggle();
                ci.cancel();
                return;
            }
        }

        if (UIInputManager.handleGlobalKeyPress(key, action)) {
            ci.cancel();
            return;
        }

//        if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
//            MinecraftClient client = MinecraftClient.getInstance();
//            if (client != null && client.player != null && client.getNetworkHandler() != null && client.currentScreen == null && MoudClientMod.isOnMoudServer()) {
//                client.setScreen(new MoudPauseScreen());
//                ci.cancel();
//                return;
//            }
//        }

        VoiceChatController.handleKeyEvent(key, action);

        if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.input != null) {
            if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_RELEASE) {
                boolean handled = ClientAPIService.INSTANCE.input.handleKeyEvent(key, action);
                if (handled) {
                    ci.cancel();
                }
            }
        }
    }

    @Inject(method = "onChar(JII)V", at = @At("HEAD"), cancellable = true)
    private void moud_onChar(long window, int codePoint, int modifiers, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return;
        }

        if (EditorImGuiLayer.getInstance().handleCharEvent(codePoint)) {
            ci.cancel();
            return;
        }

        if (EditorModeManager.getInstance().consumeCharEvent(codePoint)) {
            ci.cancel();
            return;
        }

        if (UIInputManager.handleGlobalCharTyped((char) codePoint)) {
            ci.cancel();
        }
    }
}
