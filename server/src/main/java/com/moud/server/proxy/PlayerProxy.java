package com.moud.server.proxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerProxy {
    private final Player player;
    private final ClientProxy client;

    public PlayerProxy(Player player) {
        this.player = player;
        this.client = new ClientProxy(player);
    }

    public String getName() {
        return player.getUsername();
    }

    public String getUuid() {
        return player.getUuid().toString();
    }

    public void sendMessage(String message) {
        player.sendMessage(message);
    }

    public void kick(String reason) {
        player.kick(reason);
    }

    public boolean isOnline() {
        return player.isOnline();
    }

    public ClientProxy getClient() {
        return client;
    }

    public static class ClientProxy {
        // Crée une instance statique de l'ObjectMapper pour la performance
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
        private static final Logger LOGGER = LoggerFactory.getLogger(ClientProxy.class);

        private final Player player;

        public ClientProxy(Player player) {
            this.player = player;
        }

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
}