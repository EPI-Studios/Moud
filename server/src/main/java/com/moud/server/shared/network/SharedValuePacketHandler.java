package com.moud.server.shared.network;

import com.moud.server.network.SharedValuePackets;
import com.moud.server.shared.SharedValueManager;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SharedValuePacketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SharedValuePacketHandler.class);

    private final SharedValueManager manager;

    public SharedValuePacketHandler(SharedValueManager manager) {
        this.manager = manager;
    }

    public void initialize() {
        MinecraftServer.getGlobalEventHandler()
                .addListener(PlayerPluginMessageEvent.class, this::handlePluginMessage);
        LOGGER.debug("SharedValuePacketHandler initialized");
    }

    private void handlePluginMessage(PlayerPluginMessageEvent event) {
        String channel = event.getIdentifier();
        Player player = event.getPlayer();
        byte[] data = event.getMessage();

        if ("moud:update_shared_value".equals(channel)) {
            handleClientUpdate(player, data);
        }
    }

    private void handleClientUpdate(Player player, byte[] data) {
        try {
            SharedValuePackets.ClientUpdateValueData packet =
                    new SharedValuePackets.ClientUpdateValueData(data);

            boolean success = manager.handleClientUpdate(
                    player,
                    packet.getStoreName(),
                    packet.getKey(),
                    packet.getValue()
            );

            if (!success) {
                LOGGER.warn("Rejected client update from {}: {}.{}",
                        player.getUsername(), packet.getStoreName(), packet.getKey());
            }

        } catch (Exception e) {
            LOGGER.error("Failed to handle client update from {}", player.getUsername(), e);
        }
    }
}