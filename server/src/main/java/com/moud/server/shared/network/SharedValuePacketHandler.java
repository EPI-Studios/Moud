package com.moud.server.shared.network;

import com.moud.network.MoudPackets;
import com.moud.server.network.ServerPacketWrapper;
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
        ServerPacketWrapper.registerHandler(MoudPackets.ClientUpdateValuePacket.class, this::handleClientUpdate);
        LOGGER.debug("SharedValuePacketHandler initialized");
    }

    private void handleClientUpdate(Object player, MoudPackets.ClientUpdateValuePacket packet) {
        Player minestomPlayer = (Player) player;
        try {
            boolean success = manager.handleClientUpdate(
                    minestomPlayer,
                    packet.storeName(),
                    packet.key(),
                    packet.value()
            );

            if (!success) {
                LOGGER.warn("Rejected client update from {}: {}.{}",
                        minestomPlayer.getUsername(), packet.storeName(), packet.key());
            }

        } catch (Exception e) {
            LOGGER.error("Failed to handle client update from {}", minestomPlayer.getUsername(), e);
        }
    }
}