package com.moud.server.plugin.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.network.MoudPackets;
import com.moud.plugin.api.player.PlayerContext;
import com.moud.plugin.api.services.NetworkService;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;

public final class NetworkServiceImpl implements NetworkService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Logger logger;

    public NetworkServiceImpl(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void broadcast(String eventName, String jsonPayload) {
        sendPacket(null, eventName, jsonPayload);
    }

    @Override
    public void broadcast(String eventName, Object payload) {
        broadcast(eventName, encode(payload));
    }

    @Override
    public void send(PlayerContext player, String eventName, String jsonPayload) {
        send(player.player(), eventName, jsonPayload);
    }

    @Override
    public void send(PlayerContext player, String eventName, Object payload) {
        send(player.player(), eventName, encode(payload));
    }

    @Override
    public void send(Player player, String eventName, String jsonPayload) {
        ServerNetworkManager.getInstance().sendScriptEvent(player, eventName, jsonPayload);
    }

    @Override
    public void send(Player player, String eventName, Object payload) {
        send(player, eventName, encode(payload));
    }

    private void sendPacket(Player player, String eventName, String payload) {
        ServerNetworkManager manager = ServerNetworkManager.getInstance();
        if (manager == null) {
            logger.warn("Cannot send script event {}, network manager not initialized", eventName);
            return;
        }
        if (player == null) {
            manager.broadcast(new MoudPackets.ClientboundScriptEventPacket(eventName, payload == null ? "" : payload));
        } else {
            manager.sendScriptEvent(player, eventName, payload == null ? "" : payload);
        }
    }

    private String encode(Object payload) {
        if (payload == null) {
            return "";
        }
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            logger.error("Failed to encode payload {}", payload, e);
            return "";
        }
    }
}
