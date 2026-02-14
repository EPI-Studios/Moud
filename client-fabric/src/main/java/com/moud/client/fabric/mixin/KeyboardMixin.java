package com.moud.client.fabric.mixin;

import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlay;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public final class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void moud$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
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
        if (key == GLFW.GLFW_KEY_F8) {
            return;
        }
        overlay.pushKeyEvent(key, scancode, action, modifiers);
        ci.cancel();
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
