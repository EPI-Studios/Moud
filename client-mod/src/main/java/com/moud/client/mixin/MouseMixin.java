package com.moud.client.mixin;

import com.moud.client.camera.CameraManager;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {

    @Shadow private double x;
    @Shadow private double y;

    @Unique
    private double lastX;
    @Unique
    private double lastY;

    @Inject(method = "updateMouse", at = @At("HEAD"))
    private void captureMouseDelta(CallbackInfo ci) {
        if (CameraManager.isCameraActive()) {
            double deltaX = x - lastX;
            double deltaY = y - lastY;

            if (deltaX != 0 || deltaY != 0) {
                CameraManager.handleInput(deltaX, deltaY, 0);
            }

            lastX = x;
            lastY = y;
        }
    }

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void onMouseMove(CallbackInfo ci) {
        if (CameraManager.isCameraActive()) {
            ci.cancel();
        }
    }
}