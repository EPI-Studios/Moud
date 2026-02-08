package com.moud.server.audio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerAudioManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerAudioManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final ServerAudioManager INSTANCE = new ServerAudioManager();

    private final Map<Player, Map<String, Boolean>> playerSoundRegistry = new ConcurrentHashMap<>();

    private ServerAudioManager() {
    }

    public static ServerAudioManager getInstance() {
        return INSTANCE;
    }

    public void play(Player player, Map<String, Object> data) {
        String id = (String) data.get("id");
        if (id == null || id.isEmpty()) {
            LOGGER.warn("audio:play payload missing id");
            return;
        }
        getOrCreateRegistry(player).put(id, Boolean.TRUE);
        send(player, "audio:play", data);
    }

    public void update(Player player, Map<String, Object> data) {
        String id = (String) data.get("id");
        if (id == null || id.isEmpty()) {
            LOGGER.warn("audio:update payload missing id");
            return;
        }
        if (!getOrCreateRegistry(player).containsKey(id)) {
            LOGGER.debug("audio:update ignored for unknown sound '{}'", id);
            return;
        }
        send(player, "audio:update", data);
    }

    public void stop(Player player, Map<String, Object> data) {
        String id = (String) data.get("id");
        if (id != null) {
            Map<String, Boolean> registry = getOrCreateRegistry(player);
            registry.remove(id);
        }
        send(player, "audio:stop", data);
    }

    private void send(Player player, String event, Map<String, Object> payload) {
        try {
            String json = payload == null ? "" : MAPPER.writeValueAsString(payload);
            ServerNetworkManager manager = ServerNetworkManager.getInstance();
            if (manager != null) {
                manager.sendScriptEvent(player, event, json);
            }
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialise payload for {}", event, e);
        }
    }

    private Map<String, Boolean> getOrCreateRegistry(Player player) {
        return playerSoundRegistry.computeIfAbsent(player, key -> new ConcurrentHashMap<>());
    }
}
