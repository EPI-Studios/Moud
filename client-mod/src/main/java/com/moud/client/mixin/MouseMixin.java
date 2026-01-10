package com.moud.client.mixin;

import com.moud.client.api.service.ClientAPIService;
import com.moud.client.editor.EditorModeManager;
import com.moud.client.editor.ui.EditorImGuiLayer;
import com.moud.client.movement.ClientMovementTracker;
import com.moud.client.network.ClientPacketWrapper;
import com.moud.client.ui.UIOverlayManager;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Shadow @Final private MinecraftClient client;
    @Shadow private double x;
    @Shadow private double y;

    private boolean firstMouseMove = true;
    private double lastX = 0.0;
    private double lastY = 0.0;

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void moud_onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (client.currentScreen != null) {
            return;
        }
        if (EditorImGuiLayer.getInstance().handleScroll(horizontal, vertical)) {
            ci.cancel();
            return;
        }

        if (EditorModeManager.getInstance().consumeMouseScroll(horizontal, vertical)) {
            ci.cancel();
            return;
        }

        if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.input != null) {
            if (ClientAPIService.INSTANCE.input.triggerScrollEvent(vertical)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "lockCursor", at = @At("HEAD"))
    private void onCursorLock(CallbackInfo ci) {
        this.firstMouseMove = true;
    }

    @Inject(method = "unlockCursor", at = @At("HEAD"))
    private void onCursorUnlock(CallbackInfo ci) {
        this.firstMouseMove = true;
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void moud_onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
      //  System.out.println("[MoudMouse] onMouseButton - button: " + button + ", action: " + action);

        if (client.currentScreen != null) {
            return;
        }

        boolean imguiHandled = EditorImGuiLayer.getInstance().handleMouseButton(button, action, mods);
        //System.out.println("[MoudMouse] EditorImGuiLayer.handleMouseButton returned: " + imguiHandled);
        if (imguiHandled) {
            ci.cancel();
            return;
        }

        boolean editorHandled = EditorModeManager.getInstance().consumeMouseButton(button, action, mods, this.x, this.y);
        //System.out.println("[MoudMouse] EditorModeManager.consumeMouseButton returned: " + editorHandled);
        if (editorHandled) {
            ci.cancel();
            return;
        }

        if (action != 1) {
            //  System.out.println("[MoudMouse] action is not PRESS (1), skipping");
            return;
        }

        boolean cursorLocked = client.mouse.isCursorLocked();
        //System.out.println("[MoudMouse] cursor locked: " + cursorLocked);

        if (!cursorLocked) {
            boolean overlayHandled = UIOverlayManager.getInstance().handleOverlayClick(this.x, this.y, button);
            //  System.out.println("[MoudMouse] UIOverlayManager.handleOverlayClick returned: " + overlayHandled);
            if (overlayHandled) {
                ci.cancel();
            }
            //System.out.println("[MoudMouse] cursor not locked, returning without canceling");
            return;
        }
        ClientPacketWrapper.sendToServer(new MoudPackets.PlayerClickPacket(button));
    }

    @Inject(method = "updateMouse", at = @At("HEAD"))
    private void moud_updateMouse(CallbackInfo ci) {
        double currentX = client.mouse.getX();
        double currentY = client.mouse.getY();

        if (client.currentScreen != null) {
            return;
        }

        if (EditorImGuiLayer.getInstance().handleMouseMove(currentX, currentY)) {
            lastX = currentX;
            lastY = currentY;
            firstMouseMove = false;
            return;
        }

        if (EditorModeManager.getInstance().consumeMouseDelta(currentX - lastX, currentY - lastY)) {
            lastX = currentX;
            lastY = currentY;
            return;
        }

        if (firstMouseMove) {
            lastX = currentX;
            lastY = currentY;
            firstMouseMove = false;
            return;
        }

        double dx = currentX - lastX;
        double dy = currentY - lastY;

        if (Math.abs(dx) > 0.01 || Math.abs(dy) > 0.01) {
            ClientMovementTracker.getInstance().queueMouseDelta(dx, dy);
            if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.input != null) {
                ClientAPIService.INSTANCE.input.triggerMouseMoveEvent(dx, dy);
            }
        }

        lastX = currentX;
        lastY = currentY;
    }
}
