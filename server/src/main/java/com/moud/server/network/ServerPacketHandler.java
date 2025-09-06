package com.moud.server.network;

import com.moud.server.events.EventDispatcher;
import com.moud.server.network.ServerNetworkPackets.HelloPacket;
import com.moud.server.network.ServerNetworkPackets.ServerboundScriptEventPacket;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class ServerPacketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerPacketHandler.class);
    private static final int SUPPORTED_PROTOCOL_VERSION = 1;

    private final EventDispatcher eventDispatcher;
    private final Map<Player, Boolean> moudClients = new ConcurrentHashMap<>();

    public ServerPacketHandler(EventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
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
        LOGGER.info("Player {} connected with Moud client (protocol: {})",
                player.getUsername(), clientVersion);
    }

    public void handleScriptEvent(ServerboundScriptEventPacket packet, Player player) {
        if (!isMoudClient(player)) {
            LOGGER.warn("Received script event from non-Moud client: {}", player.getUsername());
            return;
        }

        if (!isValidEventName(packet.getEventName())) {
            LOGGER.warn("Invalid event name from {}: {}", player.getUsername(), packet.getEventName());
            return;
        }

        LOGGER.debug("Received script event from {}: {} -> {}",
                player.getUsername(), packet.getEventName(), packet.getEventData());

        eventDispatcher.dispatchScriptEvent(packet.getEventName(), packet.getEventData(), player);
    }

    public void onPlayerDisconnect(Player player) {
        moudClients.remove(player);
    }

    public boolean isMoudClient(Player player) {
        return moudClients.getOrDefault(player, false);
    }

    private boolean isValidEventName(String eventName) {
        return eventName != null &&
                !eventName.trim().isEmpty() &&
                eventName.matches("^[a-zA-Z0-9:._-]+$") &&
                eventName.length() <= 128;
    }
}