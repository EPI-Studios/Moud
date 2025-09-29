
package com.moud.client.shared.network;
import com.moud.network.MoudPackets;
import com.moud.client.network.ClientPacketWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientPacketSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientPacketSender.class);

    public void sendUpdate(String storeName, String key, Object value) {
        try {
            MoudPackets.ClientUpdateValuePacket packet = new MoudPackets.ClientUpdateValuePacket(
                    storeName,
                    key,
                    value,
                    System.currentTimeMillis()
            );

            ClientPacketWrapper.sendToServer(packet);

            LOGGER.debug("Sent update request: {}.{} = {}", storeName, key, value);
        } catch (Exception e) {
            LOGGER.error("Failed to send shared value update", e);
        }
    }
}