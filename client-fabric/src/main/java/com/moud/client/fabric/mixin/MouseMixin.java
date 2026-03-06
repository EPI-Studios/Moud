package com.moud.client.fabric.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void moud$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        EditorContext ctx = EditorOverlayBus.get();
        if (ctx == null || !ctx.isActive()) {
            return; // in play mode, let vanilla hotbar scroll work
        }
        if (client == null || client.currentScreen != null) {
            return;
        }
        ctx.pushScrollY(vertical);
        ci.cancel();
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void moud$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        EditorContext ctx = EditorOverlayBus.get();
        if (ctx == null || !ctx.isActive()) {
            return; // in play mode, let vanilla handle clicks
        }
        if (client == null || client.currentScreen != null) {
            return;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            ctx.camera().consumeMouseButton(button, action, scaledMouseX(), scaledMouseY());
        }
        ci.cancel();
    }

    @Inject(method = "lockCursor", at = @At("HEAD"))
    private void moud$lockCursor(CallbackInfo ci) {
        EditorContext ctx = EditorOverlayBus.get();
        if (ctx != null) {
            ctx.camera().onCursorModeChanged();
        }
    }

    @Inject(method = "unlockCursor", at = @At("HEAD"))
    private void moud$unlockCursor(CallbackInfo ci) {
        EditorContext ctx = EditorOverlayBus.get();
        if (ctx != null) {
            ctx.camera().onCursorModeChanged();
        }
    }

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void moud$updateMouse(CallbackInfo ci) {
        EditorContext ctx = EditorOverlayBus.get();
        if (ctx != null && ctx.isActive()) {
            if (client == null || client.currentScreen != null) {
                return;
            }
            ci.cancel();
            return;
        }
        // dans le playmode on laisse la souris
    }

    @Inject(method = "onCursorPos", at = @At("HEAD"))
    private void moud$onCursorPos(long window, double x, double y, CallbackInfo ci) {
        EditorContext ctx = EditorOverlayBus.get();
        if (ctx != null && ctx.isActive()) {
            if (client == null || client.currentScreen != null) {
                return;
            }
            ctx.camera().consumeMouseMove(x, y);
        }
    }

    private double scaledMouseX() {
        Window window = client.getWindow();
        int w = window.getScaledWidth();
        return client.mouse.getX() * w / (double) Math.max(1, window.getWidth());
    }

    private double scaledMouseY() {
        Window window = client.getWindow();
        int h = window.getScaledHeight();
        return client.mouse.getY() * h / (double) Math.max(1, window.getHeight());
    }
}
