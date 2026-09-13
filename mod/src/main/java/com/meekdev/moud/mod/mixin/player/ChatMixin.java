package com.meekdev.moud.mod.mixin.player;

import com.meekdev.moud.mod.adapter.chat.ServerChat;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ChatMixin {

    @Shadow public ServerPlayer player;

    @Inject(method = "broadcastChatMessage", at = @At("HEAD"), cancellable = true)
    private void moud$message(PlayerChatMessage message, CallbackInfo ci) {
        if (ServerChat.vanilla(player, message.signedContent())) ci.cancel();
    }

    @Inject(method = "performUnsignedChatCommand", at = @At("HEAD"), cancellable = true)
    private void moud$command(String command, CallbackInfo ci) {
        if (ServerChat.command(player, command)) ci.cancel();
    }

    @Inject(method = "performSignedChatCommand", at = @At("HEAD"), cancellable = true)
    private void moud$signedCommand(ServerboundChatCommandSignedPacket packet, LastSeenMessages seen, CallbackInfo ci) {
        if (ServerChat.command(player, packet.command())) ci.cancel();
    }
}
