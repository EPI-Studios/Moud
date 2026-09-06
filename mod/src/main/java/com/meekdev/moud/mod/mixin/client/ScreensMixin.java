package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// suppresses pauseMenu, deathScreen, inventory and crafting
// every vanilla screen arrives here whatever opened it, so one cut covers the key, the block and
// the packet without three separate patches
@Mixin(Minecraft.class)
abstract class ScreensMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(Screen screen, CallbackInfo ci) {
        if (screen == null || !refuse(screen)) return;
        // a refused death screen would leave the player dead with nothing to press
        if (screen instanceof DeathScreen && Minecraft.getInstance().player instanceof LocalPlayer player) {
            player.respawn();
        }
        ci.cancel();
    }

    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void moud$suppressPause(boolean pauseOnly, CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.PAUSE_MENU)) ci.cancel();
    }

    private static boolean refuse(Screen screen) {
        if (screen instanceof PauseScreen) return !MoudMod.features().isOn(Feature.PAUSE_MENU);
        if (screen instanceof DeathScreen) return !MoudMod.features().isOn(Feature.DEATH_SCREEN);
        if (screen instanceof CraftingScreen) return !MoudMod.features().isOn(Feature.CRAFTING);
        if (screen instanceof InventoryScreen) return !MoudMod.features().isOn(Feature.INVENTORY);
        return false;
    }
}
