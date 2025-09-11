package com.moud.client.mixin;

import com.moud.client.api.service.ClientAPIService;
import com.moud.client.ui.UIOverlayManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void moud_onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        // press events (action == 1)
        if (action != 1) return;

        if (client.currentScreen != null) return;

        if (ClientAPIService.INSTANCE != null &&
                ClientAPIService.INSTANCE.cursor != null &&
                ClientAPIService.INSTANCE.cursor.isVisible()) {

            // raw mouse position
            Mouse mouse = (Mouse) (Object) this;
            double rawMouseX = mouse.getX();
            double rawMouseY = mouse.getY();

            boolean uiClicked = UIOverlayManager.getInstance().handleOverlayClick(rawMouseX, rawMouseY, button);

            if (uiClicked) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "onCursorPos", at = @At("HEAD"))
    private void moud_onMouseMove(long window, double x, double y, CallbackInfo ci) {
        if (client.currentScreen == null &&
                ClientAPIService.INSTANCE != null &&
                ClientAPIService.INSTANCE.cursor != null &&
                ClientAPIService.INSTANCE.cursor.isVisible()) {

            // UIOverlayManager.getInstance().handleMouseMove(x, y);
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void moud_onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (client.currentScreen == null &&
                ClientAPIService.INSTANCE != null &&
                ClientAPIService.INSTANCE.cursor != null &&
                ClientAPIService.INSTANCE.cursor.isVisible()) {

        }
    }
}