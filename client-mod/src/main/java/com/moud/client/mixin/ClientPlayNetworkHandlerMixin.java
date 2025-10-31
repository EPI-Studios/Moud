package com.moud.client.mixin;

import com.moud.client.MoudClientMod;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.DifficultyS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSpawnPositionS2CPacket;
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

    @Inject(method = "onDifficulty", at = @At("HEAD"), cancellable = true)
    private void moud_queueDifficultyUntilJoin(DifficultyS2CPacket packet, CallbackInfo ci) {
        MoudClientMod mod = MoudClientMod.getInstance();
        if (mod != null && mod.shouldBlockJoin()) {
            ClientPlayNetworkHandler handler = (ClientPlayNetworkHandler) (Object) this;
            mod.enqueuePostJoinPacket(h -> h.onDifficulty(packet));
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerSpawnPosition", at = @At("HEAD"), cancellable = true)
    private void moud_queueSpawnPositionUntilJoin(PlayerSpawnPositionS2CPacket packet, CallbackInfo ci) {
        MoudClientMod mod = MoudClientMod.getInstance();
        if (mod != null && mod.shouldBlockJoin()) {
            ClientPlayNetworkHandler handler = (ClientPlayNetworkHandler) (Object) this;
            mod.enqueuePostJoinPacket(h -> h.onPlayerSpawnPosition(packet));
            ci.cancel();
        }
    }
}
