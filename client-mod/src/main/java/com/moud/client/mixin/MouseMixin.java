package com.moud.client.mixin;

import com.moud.client.api.service.ClientAPIService;
import com.moud.client.ui.UIOverlayManager;
import com.moud.network.MoudPackets;
import com.moud.client.network.ClientPacketWrapper;
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

    @Inject(method = "lockCursor", at = @At("HEAD"))
    private void onCursorLock(CallbackInfo ci) {
        this.firstMouseMove = true;
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void moud_onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (action != 1) { // GLFW_PRESS
            return;
        }

        // If a vanilla screen is open, let it handle the input.
        if (client.currentScreen != null) {
            return;
        }

        // If the cursor is unlocked (visible), it means we are in a UI interaction mode.
        if (!client.mouse.isCursorLocked()) {
            // Check for clicks on the UIComponent overlay system.
            if (UIOverlayManager.getInstance().handleOverlayClick(this.x, this.y, button)) {
                // If the click was handled by the overlay, consume the event.
                ci.cancel();
                return;
            }

            // We still cancel the event to prevent Minecraft from re-locking the cursor
            // when clicking on empty space in UI mode.
            ci.cancel();
            return;
        }

        // If we reach here, the cursor is locked, and no screen is open. This is a normal gameplay click.
        ClientPacketWrapper.sendToServer(new MoudPackets.PlayerClickPacket(button));
    }

    @Inject(method = "updateMouse", at = @At("HEAD"))
    private void moud_updateMouse(CallbackInfo ci) {
        double currentX = client.mouse.getX();
        double currentY = client.mouse.getY();

        if (firstMouseMove) {
            lastX = currentX;
            lastY = currentY;
            firstMouseMove = false;
            return;
        }

        double dx = currentX - lastX;
        double dy = currentY - lastY;

        if (Math.abs(dx) > 0.01 || Math.abs(dy) > 0.01) {
            ClientPacketWrapper.sendToServer(new MoudPackets.MouseMovementPacket((float) dx, (float) dy));
            if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.input != null) {
                ClientAPIService.INSTANCE.input.triggerMouseMoveEvent(dx, dy);
            }
        }

        lastX = currentX;
        lastY = currentY;
    }
}