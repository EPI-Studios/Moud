package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.ClientPlace;
import com.meekdev.moud.mod.client.GameState;
import com.meekdev.moud.mod.client.Launch;
import com.meekdev.moud.mod.client.screens.PlaceMessageScreen;
import com.meekdev.moud.mod.client.screens.PlacePauseScreen;
import com.meekdev.moud.mod.features.Feature;
import com.meekdev.moud.mod.place.Game;
import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class ScreensMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(Screen screen, CallbackInfo ci) {
        if (screen != null && Game.standalone() && exported(screen)) {
            ci.cancel();
            return;
        }
        if (screen == null || !refuse(screen)) return;
        if (screen instanceof DeathScreen && Minecraft.getInstance().player instanceof LocalPlayer player) {
            player.respawn();
        }
        ci.cancel();
    }

    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void moud$suppressPause(boolean pauseOnly, CallbackInfo ci) {
        if (!pauseOnly && ClientPlace.pauseRequested(GameState.INSTANCE.reason())) {
            ci.cancel();
            return;
        }
        if (!MoudMod.features().isOn(Feature.PAUSE_MENU)) ci.cancel();
    }

    private static boolean exported(Screen screen) {
        Minecraft client = Minecraft.getInstance();
        if (screen instanceof TitleScreen && Launch.opened()) {
            client.stop();
            return true;
        }
        if (screen instanceof JoinMultiplayerScreen || screen instanceof SelectWorldScreen || screen instanceof RealmsMainScreen) return true;
        if (screen instanceof PauseScreen && MoudMod.features().isOn(Feature.PAUSE_MENU)) {
            client.setScreen(new PlacePauseScreen());
            return true;
        }
        if (screen instanceof DisconnectedScreen disconnected) {
            client.setScreen(new PlaceMessageScreen(Component.literal("Disconnected"), ((DisconnectedDetails) disconnected).moud$details().reason()));
            return true;
        }
        return false;
    }

    private static boolean refuse(Screen screen) {
        if (screen instanceof PauseScreen) return !MoudMod.features().isOn(Feature.PAUSE_MENU);
        if (screen instanceof DeathScreen) return !MoudMod.features().isOn(Feature.DEATH_SCREEN);
        if (screen instanceof CraftingScreen) return !MoudMod.features().isOn(Feature.CRAFTING);
        if (screen instanceof InventoryScreen) return !MoudMod.features().isOn(Feature.INVENTORY);
        return false;
    }
}
