
package com.moud.server.lighting;

import com.moud.server.network.ServerNetworkManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.minestom.server.entity.Player;
import net.minestom.server.coordinate.Vec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;

public class ServerLightingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerLightingManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static ServerLightingManager instance;

    private final Map<Long, ServerLight> lights = new ConcurrentHashMap<>();
    private final AtomicLong lightIdCounter = new AtomicLong(1);

    private ServerLightingManager() {}

    public static ServerLightingManager getInstance() {
        if (instance == null) {
            instance = new ServerLightingManager();
        }
        return instance;
    }

    public long createPointLight(double x, double y, double z, double radius, double r, double g, double b, double brightness) {
        long id = lightIdCounter.getAndIncrement();
        ServerLight light = new ServerLight(id, LightType.POINT, x, y, z, radius, r, g, b, brightness);
        lights.put(id, light);

        broadcastLightOperation("create", light);
        LOGGER.debug("Created point light {} at ({}, {}, {})", id, x, y, z);
        return id;
    }

    public long createAreaLight(double x, double y, double z, double width, double height, double r, double g, double b, double brightness) {
        long id = lightIdCounter.getAndIncrement();
        ServerLight light = new ServerLight(id, LightType.AREA, x, y, z, width, height, r, g, b, brightness);
        lights.put(id, light);

        broadcastLightOperation("create", light);
        LOGGER.debug("Created area light {} at ({}, {}, {})", id, x, y, z);
        return id;
    }

    public void updateLightPosition(long lightId, double x, double y, double z) {
        ServerLight light = lights.get(lightId);
        if (light != null) {
            light.x = x;
            light.y = y;
            light.z = z;
            broadcastLightOperation("update", light);
        }
    }

    public void updateLightColor(long lightId, double r, double g, double b) {
        ServerLight light = lights.get(lightId);
        if (light != null) {
            light.r = r;
            light.g = g;
            light.b = b;
            broadcastLightOperation("update", light);
        }
    }

    public void updateLightBrightness(long lightId, double brightness) {
        ServerLight light = lights.get(lightId);
        if (light != null) {
            light.brightness = brightness;
            broadcastLightOperation("update", light);
        }
    }

    public void removeLight(long lightId) {
        ServerLight light = lights.remove(lightId);
        if (light != null) {
            Map<String, Object> data = Map.of("id", lightId);
            broadcastEventToAllPlayers("lighting:remove", data);
            LOGGER.debug("Removed light {}", lightId);
        }
    }

    public void syncLightsToPlayer(Player player) {
        List<Map<String, Object>> lightData = new ArrayList<>();
        for (ServerLight light : lights.values()) {
            lightData.add(light.toMap());
        }

        Map<String, Object> data = Map.of("lights", lightData);
        sendEventToPlayer(player, "lighting:sync", data);
        LOGGER.debug("Synced {} lights to player {}", lightData.size(), player.getUsername());
    }

    private void broadcastLightOperation(String operation, ServerLight light) {
        Map<String, Object> data = Map.of(
                "operation", operation,
                "light", light.toMap()
        );
        broadcastEventToAllPlayers("lighting:operation", data);
    }

    private void broadcastEventToAllPlayers(String eventName, Map<String, Object> data) {
        try {
            String jsonData = MAPPER.writeValueAsString(data);
            for (Player player : net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                ServerNetworkManager.getInstance().sendScriptEvent(player, eventName, jsonData);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to broadcast lighting event", e);
        }
    }

    private void sendEventToPlayer(Player player, String eventName, Map<String, Object> data) {
        try {
            String jsonData = MAPPER.writeValueAsString(data);
            ServerNetworkManager.getInstance().sendScriptEvent(player, eventName, jsonData);
        } catch (Exception e) {
            LOGGER.error("Failed to send lighting event to player", e);
        }
    }

    public static class ServerLight {
        public final long id;
        public final LightType type;
        public double x, y, z;
        public double radius;
        public double width, height;
        public double r, g, b;
        public double brightness;

        public ServerLight(long id, LightType type, double x, double y, double z, double radius, double r, double g, double b, double brightness) {
            this.id = id;
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.r = r;
            this.g = g;
            this.b = b;
            this.brightness = brightness;
        }

        public ServerLight(long id, LightType type, double x, double y, double z, double width, double height, double r, double g, double b, double brightness) {
            this.id = id;
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = width;
            this.height = height;
            this.r = r;
            this.g = g;
            this.b = b;
            this.brightness = brightness;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> data = new ConcurrentHashMap<>();
            data.put("id", id);
            data.put("type", type.name().toLowerCase());
            data.put("x", x);
            data.put("y", y);
            data.put("z", z);
            data.put("r", r);
            data.put("g", g);
            data.put("b", b);
            data.put("brightness", brightness);

            if (type == LightType.POINT) {
                data.put("radius", radius);
            } else if (type == LightType.AREA) {
                data.put("width", width);
                data.put("height", height);
            }

            return data;
        }
    }

    public enum LightType {
        POINT, AREA
    }
}