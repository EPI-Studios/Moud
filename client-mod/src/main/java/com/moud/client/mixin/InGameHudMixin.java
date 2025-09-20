package com.moud.client.mixin;

import com.moud.client.player.PlayerStateManager;
import com.moud.client.ui.UIOverlayManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void moud_hideHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (PlayerStateManager.getInstance().isHotbarHidden()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void moud_hideExperienceBar(DrawContext context, int x, CallbackInfo ci) {
        if (PlayerStateManager.getInstance().isExperienceHidden()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
    private void moud_hideHealthAndFood(DrawContext context, CallbackInfo ci) {
        if (PlayerStateManager.getInstance().isHealthHidden() && PlayerStateManager.getInstance().isFoodHidden()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void moud_hideCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (PlayerStateManager.getInstance().isCrosshairHidden()) {
            ci.cancel();
        }
    }
}