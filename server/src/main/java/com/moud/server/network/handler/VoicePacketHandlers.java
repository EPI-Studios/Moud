package com.moud.server.network.handler;

import com.moud.network.MoudPackets.*;
import com.moud.server.audio.ServerMicrophoneManager;
import com.moud.server.audio.ServerVoiceChatManager;
import com.moud.server.events.EventDispatcher;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.plugin.PluginEventBus;
import net.minestom.server.entity.Player;

public final class VoicePacketHandlers implements PacketHandlerGroup {
    private final ServerNetworkManager networkManager;
    private final EventDispatcher eventDispatcher;

    public VoicePacketHandlers(ServerNetworkManager networkManager, EventDispatcher eventDispatcher) {
        this.networkManager = networkManager;
        this.eventDispatcher = eventDispatcher;
    }

    @Override
    public void register(PacketRegistry registry) {
        registry.register(VoiceMicrophoneChunkPacket.class, this::handleVoiceChunk);
        registry.register(ServerboundScriptEventPacket.class, this::handleScriptEvent);
    }

    private void handleVoiceChunk(Player player, VoiceMicrophoneChunkPacket packet) {
        if (!networkManager.isMoudClient(player)) {
            return;
        }
        ServerVoiceChatManager.getInstance().handleVoiceChunk(player, packet);
    }

    private void handleScriptEvent(Player player, ServerboundScriptEventPacket packet) {
        if (!networkManager.isMoudClient(player)) {
            return;
        }
        // handle audio events
        if (packet.eventName().startsWith("audio:microphone:")) {
            ServerMicrophoneManager.getInstance()
                    .handleEvent(player, packet.eventName(), packet.eventData());
        }

        eventDispatcher.dispatchScriptEvent(packet.eventName(), packet.eventData(), player);
        PluginEventBus.getInstance().dispatchScriptEvent(packet.eventName(), player, packet.eventData());
    }
}
