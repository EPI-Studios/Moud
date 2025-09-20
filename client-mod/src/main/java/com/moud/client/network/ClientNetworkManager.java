package com.moud.client.network;

import com.moud.network.MoudPackets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientNetworkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientNetworkManager.class);

    public static void sendToServer(String eventName, String data) {
        try {
            MoudPackets.ServerboundScriptEventPacket packet = new MoudPackets.ServerboundScriptEventPacket(eventName, data);
            ClientPacketWrapper.sendToServer(packet);
            LOGGER.debug("Sent script event to server: {}", eventName);
        } catch (Exception e) {
            LOGGER.error("Failed to send script event to server", e);
        }
    }
}