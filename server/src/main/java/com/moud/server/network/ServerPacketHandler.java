package com.moud.server.network;

import com.moud.server.events.EventDispatcher;
import com.moud.server.network.ServerNetworkPackets.*;
import com.moud.server.player.PlayerCameraManager;
import com.moud.server.proxy.PlayerModelProxy;
import net.minestom.server.entity.Player;
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
        LOGGER.debug("Dispatching script event '{}' from player {}", packet.getEventName(), player.getUsername());
        eventDispatcher.dispatchScriptEvent(packet.getEventName(), packet.getEventData(), player);
    }

    public void handleCameraUpdate(ClientUpdateCameraPacket packet, Player player) {
        if (!isMoudClient(player)) return;
        // LOGGER.debug("Camera update from {}: {}", player.getUsername(), packet.getDirection());
        PlayerCameraManager.getInstance().updateCameraDirection(player, packet.getDirection());
    }

    public void handlePlayerModelClick(PlayerModelPackets.ServerboundPlayerModelClickPacket packet, Player player) {
        if (!isMoudClient(player)) return;
        LOGGER.debug("Player model click from {}: model {} at ({}, {})",
                player.getUsername(), packet.getModelId(), packet.getMouseX(), packet.getMouseY());
        PlayerModelProxy model = PlayerModelProxy.getById(packet.getModelId());
        if (model != null) {
            model.triggerClick(player, packet.getMouseX(), packet.getMouseY(), packet.getButton());
        }
    }

    public void handleMouseMovement(C2S_MouseMovementPacket packet, Player player) {
        if (!isMoudClient(player)) return;
        LOGGER.trace("Mouse movement from {}: dx={}, dy={}",
                player.getUsername(), packet.deltaX, packet.deltaY);
        eventDispatcher.dispatchMouseMoveEvent(player, packet.deltaX, packet.deltaY);
    }

    public void handlePlayerClick(C2S_PlayerClickPacket packet, Player player) {
        if (!isMoudClient(player)) return;
        LOGGER.debug("Player click from {}: button={}", player.getUsername(), packet.button);
        eventDispatcher.dispatchPlayerClickEvent(player, packet.button);
    }

    public void onPlayerDisconnect(Player player) {
        moudClients.remove(player);
        PlayerCameraManager.getInstance().onPlayerDisconnect(player);
        LOGGER.debug("Player {} disconnected, cleaned up client state", player.getUsername());
    }

    public boolean isMoudClient(Player player) {
        return moudClients.getOrDefault(player, false);
    }
}