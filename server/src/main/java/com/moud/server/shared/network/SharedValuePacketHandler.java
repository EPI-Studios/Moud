package com.moud.server.shared.network;

import com.moud.network.MoudPackets;
import com.moud.server.network.ServerPacketWrapper;
import com.moud.server.shared.SharedValueManager;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SharedValuePacketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SharedValuePacketHandler.class);

    private final SharedValueManager manager;

    public SharedValuePacketHandler(SharedValueManager manager) {
        this.manager = manager;
    }

    public void initialize() {
        ServerPacketWrapper.registerPlayerHandler(MoudPackets.ClientUpdateValuePacket.class, this::handleClientUpdate);
        LOGGER.debug("SharedValuePacketHandler initialized");
    }

    private void handleClientUpdate(Player player, MoudPackets.ClientUpdateValuePacket packet) {
        try {
            boolean success = manager.handleClientUpdate(
                    player,
                    packet.storeName(),
                    packet.key(),
                    packet.value()
            );

            if (!success) {
                LOGGER.warn("Rejected client update from {}: {}.{}",
                        player.getUsername(), packet.storeName(), packet.key());
            }

        } catch (Exception e) {
            LOGGER.error("Failed to handle client update from {}", player.getUsername(), e);
        }
    }
}
