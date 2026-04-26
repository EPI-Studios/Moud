package com.moud.client.fabric.mixin;

import com.moud.client.fabric.MoudServerDetector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenStaticMixin {
    @Inject(method = "connect(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;ZLnet/minecraft/client/network/CookieStorage;)V",
            at = @At("HEAD"))
    private static void moud$captureConnectInfo(Screen parent, MinecraftClient client, ServerAddress address,
                                                ServerInfo info, boolean quickPlay, CookieStorage cookieStorage,
                                                CallbackInfo ci) {
        String raw = info != null ? info.address : (address != null ? address.getAddress() + ":" + address.getPort() : null);
        MoudServerDetector.rememberConnectAttempt(raw, info);
        if (info != null) MoudServerDetector.scanAndMark(info);
    }
}
