package com.moud.server.network;

import com.moud.server.events.EventDispatcher;
import com.moud.server.network.ServerNetworkPackets.ClientUpdateCameraPacket;
import com.moud.server.network.ServerNetworkPackets.HelloPacket;
import com.moud.server.network.ServerNetworkPackets.ServerboundScriptEventPacket;
import com.moud.server.player.PlayerCameraManager;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerPacketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerPacketHandler.class);
    private static final int SUPPORTED_PROTOCOL_VERSION = 1;

    private final EventDispatcher eventDispatcher;
    private final Map<Player, Boolean> moudClients = new ConcurrentHashMap<>();

    public ServerPacketHandler(EventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
    }

    public void handlePluginMessage(PlayerPluginMessageEvent event) {
        String channel = event.getIdentifier();
        Player player = event.getPlayer();
        byte[] data = event.getMessage();

        switch (channel) {
            case "moud:hello" -> {
                HelloPacket packet = new HelloPacket(data);
                handleHelloPacket(packet, player);
            }
            case "moud:script_event_s" -> {
                ServerboundScriptEventPacket packet = new ServerboundScriptEventPacket(data);
                handleScriptEvent(packet, player);
            }
            case "moud:update_camera" -> {
                ClientUpdateCameraPacket packet = new ClientUpdateCameraPacket(data);
                PlayerCameraManager.getInstance().updateCameraDirection(player, packet.getDirection());
            }
        }
    }

    public void handleHelloPacket(HelloPacket packet, Player player) {
        int clientVersion = packet.getProtocolVersion();
        if (clientVersion != SUPPORTED_PROTOCOL_VERSION) {
            LOGGER.warn("Player {} has unsupported Moud protocol version: {} (expected: {})",
                    player.getUsername(), clientVersion, SUPPORTED_PROTOCOL_VERSION);
            player.kick("Unsupported Moud client version");
            return;
        }
        moudClients.put(player, true);
        LOGGER.info("Player {} connected with Moud client (protocol: {})", player.getUsername(), clientVersion);
    }

    public void handleScriptEvent(ServerboundScriptEventPacket packet, Player player) {
        if (!isMoudClient(player)) {
            LOGGER.warn("Received script event from non-Moud client: {}", player.getUsername());
            return;
        }
        eventDispatcher.dispatchScriptEvent(packet.getEventName(), packet.getEventData(), player);
    }

    public void onPlayerDisconnect(Player player) {
        moudClients.remove(player);
        PlayerCameraManager.getInstance().onPlayerDisconnect(player);
    }

    public boolean isMoudClient(Player player) {
        return moudClients.getOrDefault(player, false);
    }
}