package com.moud.server.proxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientProxy {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientProxy.class);

    private final Player player;

    public ClientProxy(Player player) {
        this.player = player;
    }

    /**
     * Sends a custom event to this player's client-side script environment.
     * @param eventName The name of the event to trigger on the client.
     * @param data The data payload for the event, which will be serialized to JSON.
     */
    public void send(String eventName, Object data) {
        String serializedData;
        try {
            serializedData = data != null ? OBJECT_MAPPER.writeValueAsString(data) : "";
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize event data for event: {}", eventName, e);
            serializedData = "";
        }
        ServerNetworkManager.getInstance().sendScriptEvent(player, eventName, serializedData);
    }
}