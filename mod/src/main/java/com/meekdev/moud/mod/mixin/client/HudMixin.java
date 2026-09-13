package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Gui.class)
abstract class HudMixin {

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void moud$crosshair(CallbackInfo ci) {
        off(Feature.CROSSHAIR, ci);
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void moud$hotbarAndDecorations(CallbackInfo ci) {
        off(Feature.HOTBAR, ci);
    }

    @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
    private void moud$itemHotbar(CallbackInfo ci) {
        off(Feature.HOTBAR, ci);
    }

    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void moud$selectedItemName(CallbackInfo ci) {
        off(Feature.HOTBAR, ci);
    }

    @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void moud$health(CallbackInfo ci) {
        off(Feature.HEALTH_BAR, ci);
    }

    @Inject(method = "extractVehicleHealth", at = @At("HEAD"), cancellable = true)
    private void moud$vehicleHealth(CallbackInfo ci) {
        off(Feature.HEALTH_BAR, ci);
    }

    @Inject(method = "extractAirBubbles", at = @At("HEAD"), cancellable = true)
    private void moud$air(CallbackInfo ci) {
        off(Feature.HEALTH_BAR, ci);
    }

    @Inject(method = "extractArmor", at = @At("HEAD"), cancellable = true)
    private static void moud$armor(CallbackInfo ci) {
        off(Feature.HEALTH_BAR, ci);
    }

    @Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
    private void moud$food(CallbackInfo ci) {
        off(Feature.HUNGER_BAR, ci);
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void moud$effects(CallbackInfo ci) {
        off(Feature.EFFECT_ICONS, ci);
    }

    @Inject(method = "extractBossOverlay", at = @At("HEAD"), cancellable = true)
    private void moud$bossBar(CallbackInfo ci) {
        off(Feature.BOSS_BAR, ci);
    }

    @Inject(method = "extractChat", at = @At("HEAD"), cancellable = true)
    private void moud$chat(CallbackInfo ci) {
        off(Feature.CHAT, ci);
    }

    @Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void moud$overlayMessage(CallbackInfo ci) {
        off(Feature.CHAT, ci);
    }

    @Inject(method = "extractTabList", at = @At("HEAD"), cancellable = true)
    private void moud$tabList(CallbackInfo ci) {
        off(Feature.TAB_LIST, ci);
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void moud$scoreboard(CallbackInfo ci) {
        off(Feature.SCOREBOARD, ci);
    }

    @Inject(method = "extractSubtitleOverlay", at = @At("HEAD"), cancellable = true)
    private void moud$subtitles(CallbackInfo ci) {
        off(Feature.VANILLA_SOUNDS, ci);
    }

    @Inject(method = "nextContextualInfoState", at = @At("HEAD"), cancellable = true)
    private void moud$infoBar(CallbackInfoReturnable<Gui.ContextualInfo> cir) {
        if (!MoudMod.features().isOn(Feature.XP_BAR)) cir.setReturnValue(Gui.ContextualInfo.EMPTY);
    }

    private static void off(Feature feature, CallbackInfo ci) {
        if (!MoudMod.features().isOn(feature)) ci.cancel();
    }
}
