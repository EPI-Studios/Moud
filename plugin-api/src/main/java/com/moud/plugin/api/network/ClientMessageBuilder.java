package com.moud.plugin.api.network;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.plugin.api.player.PlayerContext;
import com.moud.plugin.api.services.ClientService;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientMessageBuilder {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClientService clients;
    private final PlayerContext player;
    private final String eventName;
    private final Map<String, Object> payload = new LinkedHashMap<>();

    private ClientMessageBuilder(ClientService clients, PlayerContext player, String eventName) {
        this.clients = clients;
        this.player = player;
        this.eventName = eventName;
    }

    public static ClientMessageBuilder toPlayer(ClientService clients, PlayerContext player, String eventName) {
        return new ClientMessageBuilder(clients, player, eventName);
    }

    public ClientMessageBuilder with(String key, Object value) {
        payload.put(key, value);
        return this;
    }

    public void dispatch() {
        try {
            String json = payload.isEmpty() ? "" : MAPPER.writeValueAsString(payload);
            clients.sendRaw(player, eventName, json);
        } catch (JsonProcessingException e) {
            clients.sendRaw(player, eventName, "");
        }
    }
}
