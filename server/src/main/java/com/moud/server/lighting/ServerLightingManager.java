package com.moud.server.lighting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ServerLightingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerLightingManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static ServerLightingManager instance;

    private final Map<Long, Map<String, Object>> lights = new ConcurrentHashMap<>();
    private final AtomicLong lightIdCounter = new AtomicLong(0L);

    private ServerLightingManager() {}

    public static synchronized ServerLightingManager getInstance() {
        if (instance == null) {
            instance = new ServerLightingManager();
        }
        return instance;
    }

    public void createOrUpdateLight(long lightId, Map<String, Object> properties) {
        boolean isNewLight = !lights.containsKey(lightId);
        Map<String, Object> lightData = lights.computeIfAbsent(lightId, k -> new ConcurrentHashMap<>());
        lightData.putAll(properties);
        lightData.put("id", lightId);
        lightIdCounter.accumulateAndGet(lightId, Math::max);

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Broadcasting light {} {} ({} properties)", lightId, isNewLight ? "create" : "update", lightData.size());
        }

        broadcastLightOperation(isNewLight ? "create" : "update", lightData);
    }
    public void removeLight(long lightId) {
        if (lights.remove(lightId) != null) {
            broadcastLightOperation("remove", Map.of("id", lightId));
        }
    }

    public long spawnLight(String type, Map<String, Object> properties) {
        long id = lightIdCounter.incrementAndGet();
        Map<String, Object> lightData = lights.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        lightData.clear();
        lightData.putAll(properties);
        lightData.put("id", id);
        lightData.put("type", type);

        broadcastLightOperation("create", lightData);
        return id;
    }

    public void syncLightsToPlayer(Player player) {
        if (lights.isEmpty()) {
            LOGGER.debug("No lights to sync to player {}.", player.getUsername());
            return;
        }

        List<Map<String, Object>> allLights = new ArrayList<>(lights.values());
        Map<String, Object> syncData = Map.of("lights", allLights);
        sendEventToPlayer(player, "lighting:sync", syncData);
        LOGGER.debug("Synced {} lights to player {}", allLights.size(), player.getUsername());
    }

    private void broadcastLightOperation(String operation, Map<String, Object> data) {
        Map<String, Object> payload = Map.of("operation", operation, "light", data);
        broadcastEventToAllPlayers("lighting:operation", payload);
    }

    private void broadcastEventToAllPlayers(String eventName, Map<String, Object> data) {
        try {
            String jsonData = MAPPER.writeValueAsString(data);
            ServerNetworkManager networkManager = ServerNetworkManager.getInstance();
            if (networkManager == null) return;

            for (Player player : net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                networkManager.sendScriptEvent(player, eventName, jsonData);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to broadcast lighting event", e);
        }
    }

    private void sendEventToPlayer(Player player, String eventName, Map<String, Object> data) {
        try {
            String jsonData = MAPPER.writeValueAsString(data);
            ServerNetworkManager networkManager = ServerNetworkManager.getInstance();
            if (networkManager != null) {
                networkManager.sendScriptEvent(player, eventName, jsonData);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to send lighting event to player {}", player.getUsername(), e);
        }
    }
}
