package com.moud.plugin.api.network;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.plugin.api.services.NetworkService;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BroadcastBuilder {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final NetworkService network;
    private final String eventName;
    private final Map<String, Object> payload = new LinkedHashMap<>();

    public BroadcastBuilder(NetworkService network, String eventName) {
        this.network = network;
        this.eventName = eventName;
    }

    public BroadcastBuilder with(String key, Object value) {
        payload.put(key, value);
        return this;
    }

    public void send() {
        try {
            String json = payload.isEmpty() ? "" : MAPPER.writeValueAsString(payload);
            network.broadcast(eventName, json);
        } catch (JsonProcessingException e) {
            network.broadcast(eventName, "");
        }
    }
}
