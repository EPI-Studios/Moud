package com.moud.client.mixin;

import com.moud.client.MoudClientMod;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onGameJoin", at = @At("HEAD"), cancellable = true)
    private void moud_delayJoinUntilResourcesLoad(GameJoinS2CPacket packet, CallbackInfo ci) {
        MoudClientMod mod = MoudClientMod.getInstance();
        if (mod != null && mod.shouldBlockJoin()) {
            mod.setPendingGameJoinPacket(packet);
            ci.cancel();
        }
    }
}