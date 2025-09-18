package com.moud.server.network;

import com.moud.server.client.ClientScriptManager;
import com.moud.server.network.ServerNetworkPackets.*;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServerNetworkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerNetworkManager.class);

    private final ServerPacketHandler packetHandler;
    private final ClientScriptManager clientScriptManager;
    private static ServerNetworkManager instance;

    public ServerNetworkManager(ServerPacketHandler packetHandler, ClientScriptManager clientScriptManager) {
        this.packetHandler = packetHandler;
        this.clientScriptManager = clientScriptManager;
        instance = this;
    }

    public static ServerNetworkManager getInstance() {
        return instance;
    }

    public void initialize() {
        registerEventHandlers();
        LOGGER.info("Server network manager initialized");
    }

    private void registerEventHandlers() {
        MinecraftServer.getGlobalEventHandler()
                .addListener(PlayerPluginMessageEvent.class, this::handlePluginMessage)
                .addListener(PlayerDisconnectEvent.class, this::handlePlayerDisconnect);
    }

    private void handlePluginMessage(PlayerPluginMessageEvent event) {
        String channel = event.getIdentifier();
        Player player = event.getPlayer();
        byte[] data = event.getMessage();

        switch (channel) {
            case "moud:hello" -> {
                LOGGER.info("Processing hello packet from {}", player.getUsername());
                HelloPacket packet = new HelloPacket(data);
                packetHandler.handleHelloPacket(packet, player);
                sendClientScripts(player);
            }
            case "moud:script_event_s" -> {
                LOGGER.debug("Processing script event from {}", player.getUsername());
                ServerboundScriptEventPacket packet = new ServerboundScriptEventPacket(data);
                packetHandler.handleScriptEvent(packet, player);
            }
            case "moud:update_camera" -> {
                LOGGER.trace("Processing camera update from {}", player.getUsername());
                ClientUpdateCameraPacket packet = new ClientUpdateCameraPacket(data);
                packetHandler.handleCameraUpdate(packet, player);
            }
            case "moud:mouse_move" -> {
                LOGGER.trace("Processing mouse movement from {}", player.getUsername());
                C2S_MouseMovementPacket packet = new C2S_MouseMovementPacket(data);
                packetHandler.handleMouseMovement(packet, player);
            }
            case "moud:player_click" -> {
                LOGGER.debug("Processing player click from {}", player.getUsername());
                C2S_PlayerClickPacket packet = new C2S_PlayerClickPacket(data);
                packetHandler.handlePlayerClick(packet, player);
            }
            case "moud:player_model_click" -> {
                LOGGER.debug("Processing player model click from {}", player.getUsername());
                PlayerModelPackets.ServerboundPlayerModelClickPacket packet = new PlayerModelPackets.ServerboundPlayerModelClickPacket(data);
                packetHandler.handlePlayerModelClick(packet, player);
            }
            default -> {
                LOGGER.trace("Ignoring unknown plugin message channel: {}", channel);
            }
        }
    }

    private void handlePlayerDisconnect(PlayerDisconnectEvent event) {
        LOGGER.debug("Handling disconnect for player: {}", event.getPlayer().getUsername());
        packetHandler.onPlayerDisconnect(event.getPlayer());
    }

    private void sendClientScripts(Player player) {
        LOGGER.debug("Checking if client scripts should be sent to {}", player.getUsername());

        if (!clientScriptManager.hasClientScripts()) {
            LOGGER.info("No client scripts available to send to {}", player.getUsername());
            return;
        }

        try {
            byte[] scriptData = clientScriptManager.getCompiledScripts();
            String hash = clientScriptManager.getScriptsHash();

            LOGGER.info("Sending client scripts to {}: hash={}, size={} bytes",
                    player.getUsername(), hash, scriptData.length);

            player.sendPacket(ServerNetworkPackets.createSyncClientScriptsPacket(hash, scriptData));

            LOGGER.info("Successfully sent client scripts to {}", player.getUsername());
        } catch (Exception e) {
            LOGGER.error("Failed to send client scripts to {}", player.getUsername(), e);
        }
    }

    public void sendScriptEvent(Player player, String eventName, String eventData) {
        if (!packetHandler.isMoudClient(player)) {
            LOGGER.warn("Attempted to send script event to non-Moud client: {}", player.getUsername());
            return;
        }

        try {
            LOGGER.debug("Sending script event to {}: {} -> {}",
                    player.getUsername(), eventName, eventData);

            player.sendPacket(ServerNetworkPackets.createClientboundScriptEventPacket(eventName, eventData));

            LOGGER.debug("Successfully sent script event to {}", player.getUsername());
        } catch (Exception e) {
            LOGGER.error("Failed to send script event to {}: {}", player.getUsername(), eventName, e);
        }
    }
}