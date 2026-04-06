package com.moud.client.fabric.mixin;

import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
            PlayRuntimeClient runtime = PlayRuntimeBus.get();
            if (runtime == null || !runtime.isActive() || !runtime.isCursorModeEnabled() || client == null || client.currentScreen != null) {
                return; // in play mode, let vanilla handle clicks unless cursor mode is active
            }
            ci.cancel();
            return;
        }
        if (client == null || client.currentScreen != null) {
            return;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            ctx.camera().consumeMouseButton(button, action, client.mouse.getX(), client.mouse.getY());
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
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime != null && runtime.isActive() && runtime.isCursorModeEnabled()) {
            if (client == null || client.currentScreen != null) {
                return;
            }
            ci.cancel();
        }
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

}
