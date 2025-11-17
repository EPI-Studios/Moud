package com.moud.server.plugin.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.plugin.api.player.PlayerContext;
import com.moud.plugin.api.services.ClientService;
import com.moud.plugin.api.services.client.ClientBridge;
import com.moud.server.plugin.player.PlayerContextImpl;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

public final class ClientServiceImpl implements ClientService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Logger logger;

    public ClientServiceImpl(Logger logger) {
        this.logger = logger;
    }

    @Override
    public Optional<ClientBridge> client(PlayerContext player) {
        if (player == null || player.player() == null) {
            return Optional.empty();
        }
        return Optional.of(new ClientBridgeImpl(player.player()));
    }

    @Override
    public Optional<ClientBridge> client(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .filter(p -> p.getUuid().equals(playerId))
                .findFirst()
                .map(ClientBridgeImpl::new);
    }

    @Override
    public void send(PlayerContext player, String eventName, Object payload) {
        client(player).ifPresent(bridge -> bridge.send(eventName, payload));
    }

    @Override
    public void sendRaw(PlayerContext player, String eventName, String jsonPayload) {
        client(player).ifPresent(bridge -> bridge.sendRaw(eventName, jsonPayload));
    }

    private String encode(Object payload) {
        if (payload == null) {
            return "";
        }
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            logger.error("Failed to encode client payload {}", payload, e);
            return "";
        }
    }

    private final class ClientBridgeImpl implements ClientBridge {
        private final Player player;
        private final PlayerContext context;

        private ClientBridgeImpl(Player player) {
            this.player = player;
            this.context = new PlayerContextImpl(player);
        }

        @Override
        public PlayerContext player() {
            return context;
        }

        @Override
        public void send(String eventName, Object payload) {
            sendRaw(eventName, encode(payload));
        }

        @Override
        public void sendRaw(String eventName, String jsonPayload) {
            ServerNetworkManager manager = ServerNetworkManager.getInstance();
            if (manager == null) {
                logger.warn("Cannot deliver client event {} - network manager unavailable", eventName);
                return;
            }
            manager.sendScriptEvent(player, eventName, jsonPayload == null ? "" : jsonPayload);
        }
    }
}
