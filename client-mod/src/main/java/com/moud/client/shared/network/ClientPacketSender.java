package com.moud.client.shared.network;

import com.moud.client.network.packets.ClientUpdateValuePacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientPacketSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientPacketSender.class);

    public void sendUpdate(String storeName, String key, Object value) {
        if (!ClientPlayNetworking.canSend(ClientUpdateValuePacket.ID)) {
            LOGGER.warn("Cannot send shared value update: not connected to Moud server");
            return;
        }

        long timestamp = System.currentTimeMillis();
        ClientUpdateValuePacket packet = new ClientUpdateValuePacket(storeName, key, value, timestamp);

        try {
            ClientPlayNetworking.send(packet);
            LOGGER.debug("Sent update request: {}.{} = {}", storeName, key, value);
        } catch (Exception e) {
            LOGGER.error("Failed to send shared value update", e);
        }
    }
}