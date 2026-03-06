package com.moud.client.fabric.mixin;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Keyboard.class)
public final class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void moud$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        EditorContext ctx = EditorOverlayBus.get();
        EditorOverlay overlay = ctx != null ? ctx.overlay() : null;

        if (overlay != null && overlay.isOpen()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.currentScreen == null && key != GLFW.GLFW_KEY_F8) {
                overlay.pushKeyEvent(key, scancode, action, modifiers);
                ci.cancel();
            }
            return;
        }

        // In play mode only intercept Escape to open the pause menu; let vanilla handle everything else.
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        MinecraftClient client = MinecraftClient.getInstance();
        if (runtime == null || !runtime.isActive() || client == null || client.currentScreen != null) {
            return;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
            client.setScreen(new GameMenuScreen(true));
            ci.cancel();
        }
    }

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void moud$onChar(long window, int codePoint, int modifiers, CallbackInfo ci) {
        EditorContext ctx = EditorOverlayBus.get();
        if (ctx == null) {
            return;
        }
        EditorOverlay overlay = ctx.overlay();
        if (overlay == null || !overlay.isOpen()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.currentScreen != null) {
            return;
        }
        overlay.pushCharEvent(codePoint);
        ci.cancel();
    }
}
