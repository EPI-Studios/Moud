package com.moud.client.mixin;

import com.moud.client.api.service.ClientAPIService;
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
        if (client.currentScreen != null || action != 1) {
            return;
        }

        if (ClientAPIService.INSTANCE != null && ClientAPIService.INSTANCE.ui != null) {
            if (ClientAPIService.INSTANCE.ui.handleClick(this.x, this.y, button)) {
                ci.cancel();
                return;
            }
        }

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