package com.moud.client.fabric.mixin;

import com.moud.client.fabric.render.McHudOverrides;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void moud$hideCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (McHudOverrides.hideCrosshair) ci.cancel();
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true, require = 0)
    private void moud$hideHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (McHudOverrides.hideHotbar) ci.cancel();
    }

    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true, require = 0)
    private void moud$hideStatusBars(DrawContext context, CallbackInfo ci) {
        if (McHudOverrides.hideStatusBars) ci.cancel();
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true, require = 0)
    private void moud$hideExperienceBar(DrawContext context, int x, CallbackInfo ci) {
        if (McHudOverrides.hideExperienceBar) ci.cancel();
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void moud$hideStatusEffects(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (McHudOverrides.hideStatusEffects) ci.cancel();
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void moud$hideScoreboard(DrawContext context, net.minecraft.scoreboard.ScoreboardObjective objective, CallbackInfo ci) {
        if (McHudOverrides.hideScoreboard) ci.cancel();
    }

    @Inject(method = "renderChat", at = @At("HEAD"), cancellable = true, require = 0)
    private void moud$hideChat(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (McHudOverrides.hideChat) ci.cancel();
    }

    @Inject(method = "renderPlayerList", at = @At("HEAD"), cancellable = true, require = 0)
    private void moud$hidePlayerList(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (McHudOverrides.hidePlayerList) ci.cancel();
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private void moud$hideHeldItemTooltip(DrawContext context, CallbackInfo ci) {
        if (McHudOverrides.hideHeldItemTooltip) ci.cancel();
    }

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true, require = 0)
    private void moud$hideOverlayMessage(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (McHudOverrides.hideOverlayMessage) ci.cancel();
    }

    @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true, require = 0)
    private void moud$hideTitle(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (McHudOverrides.hideTitleAndSubtitle) ci.cancel();
    }

    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void moud$hideVignette(DrawContext context, net.minecraft.entity.Entity entity, CallbackInfo ci) {
        if (McHudOverrides.hideVignette) ci.cancel();
    }

}
