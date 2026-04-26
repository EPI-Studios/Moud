package com.moud.client.fabric.mixin;

import com.moud.client.fabric.render.loading.PlayLoading;
import com.moud.client.fabric.render.loading.PlayLoadingOverlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenPlayLoadingOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void moud$drawPlayLoadingOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (PlayLoading.isActive()) {
            PlayLoadingOverlay.render(context);
        }
    }
}
