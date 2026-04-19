package com.moud.client.fabric.mixin;

import com.moud.client.fabric.player.PlayerBodyAttachmentCache;
import com.moud.client.fabric.player.PlayerBodyScale;
import com.moud.client.fabric.player.PlayerBodyVisibility;
import java.util.UUID;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onPlayerRemove", at = @At("HEAD"))
    private void moud$onPlayerRemove(PlayerRemoveS2CPacket packet, CallbackInfo ci) {
        for (UUID uuid : packet.profileIds()) {
            PlayerBodyAttachmentCache.clearPlayer(uuid.toString());
            PlayerBodyVisibility.clearPlayer(uuid.toString());
            PlayerBodyScale.clearPlayer(uuid.toString());
        }
    }
}
