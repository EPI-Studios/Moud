package com.moud.client.mixin;

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
        if (action == 1 && client.currentScreen == null) {
            Mouse mouse = (Mouse) (Object) this;
            double rawMouseX = mouse.getX();
            double rawMouseY = mouse.getY();

            int scaledWidth = client.getWindow().getScaledWidth();
            int scaledHeight = client.getWindow().getScaledHeight();
            int windowWidth = client.getWindow().getWidth();
            int windowHeight = client.getWindow().getHeight();

            double mouseX = rawMouseX * scaledWidth / windowWidth;
            double mouseY = rawMouseY * scaledHeight / windowHeight;

            UIOverlayManager.getInstance().handleOverlayClick(mouseX, mouseY, button);
        }
    }
}