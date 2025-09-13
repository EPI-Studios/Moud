
package com.moud.client.lighting;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.Light;
import foundry.veil.api.client.render.light.PointLight;
import foundry.veil.api.client.render.light.AreaLight;
import com.moud.api.math.Conversion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientLightingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientLightingService.class);
    private static final float LIGHT_RENDER_DISTANCE = 128.0f;

    private final Map<Long, ManagedLight> managedLights = new ConcurrentHashMap<>();
    private final MinecraftClient client;

    public ClientLightingService() {
        this.client = MinecraftClient.getInstance();
    }


    public void handleLightOperation(Map<String, Object> data) {
        String operation = (String) data.get("operation");
        Map<String, Object> lightData = (Map<String, Object>) data.get("light");

        switch (operation) {
            case "create" -> createLight(lightData);
            case "update" -> updateLight(lightData);
        }
    }

    public void handleLightSync(Map<String, Object> data) {
        clearAllLights();

        java.util.List<Map<String, Object>> lights = (java.util.List<Map<String, Object>>) data.get("lights");
        for (Map<String, Object> lightData : lights) {
            createLight(lightData);
        }
        LOGGER.debug("Synced {} lights from server", lights.size());
    }

    public void handleRemoveLight(Map<String, Object> data) {
        long id = Conversion.toLong(data.get("id"));
        removeLight(id);
    }
    private void createLight(Map<String, Object> data) {
        Number idNumber = (Number) data.get("id");
        long id = idNumber.longValue();

        String type = (String) data.get("type");
        double x = ((Number) data.get("x")).doubleValue();
        double y = ((Number) data.get("y")).doubleValue();
        double z = ((Number) data.get("z")).doubleValue();
        float r = ((Number) data.get("r")).floatValue();
        float g = ((Number) data.get("g")).floatValue();
        float b = ((Number) data.get("b")).floatValue();
        float brightness = ((Number) data.get("brightness")).floatValue();

        ManagedLight managed = new ManagedLight(id, type, x, y, z, r, g, b, brightness);

        if ("point".equals(type)) {
            managed.radius = ((Number) data.get("radius")).floatValue();
        } else if ("area".equals(type)) {
            managed.width = ((Number) data.get("width")).floatValue();
            managed.height = ((Number) data.get("height")).floatValue();
        }

        managedLights.put(id, managed);
        updateLightVisibility(managed);

        LOGGER.debug("Created {} light {} at ({}, {}, {})", type, id, x, y, z);
    }
    private void updateLight(Map<String, Object> data) {
        long id = Conversion.toLong(data.get("id"));

        ManagedLight managed = managedLights.get(id);
        if (managed == null) return;

        if (data.containsKey("x")) managed.x = Conversion.toDouble(data.get("x"));
        if (data.containsKey("y")) managed.y = Conversion.toDouble(data.get("y"));
        if (data.containsKey("z")) managed.z = Conversion.toDouble(data.get("z"));

        if (data.containsKey("r")) managed.r = Conversion.toFloat(data.get("r"));
        if (data.containsKey("g")) managed.g = Conversion.toFloat(data.get("g"));
        if (data.containsKey("b")) managed.b = Conversion.toFloat(data.get("b"));

        if (data.containsKey("brightness")) managed.brightness = Conversion.toFloat(data.get("brightness"));

        if ("point".equals(managed.type) && data.containsKey("radius")) {
            managed.radius = Conversion.toFloat(data.get("radius"));
        } else if ("area".equals(managed.type) && data.containsKey("width")) {
            managed.width = Conversion.toFloat(data.get("width"));
            managed.height = Conversion.toFloat(data.get("height"));
        }
        updateActiveLight(managed);
    }


    private void removeLight(long id) {
        ManagedLight managed = managedLights.remove(id);
        if (managed != null) {
            destroyVeilLight(managed);
            LOGGER.debug("Removed light {}", id);
        }
    }

    private void clearAllLights() {
        for (ManagedLight managed : managedLights.values()) {
            destroyVeilLight(managed);
        }
        managedLights.clear();
    }

    public void tick() {
        for (ManagedLight managed : managedLights.values()) {
            updateLightVisibility(managed);
        }
    }

    private void updateLightVisibility(ManagedLight managed) {
        boolean shouldBeVisible = isPlayerWithinRange(managed.x, managed.y, managed.z);

        if (shouldBeVisible && managed.veilLight == null) {
            createVeilLight(managed);
        } else if (!shouldBeVisible && managed.veilLight != null) {
            destroyVeilLight(managed);
        }
    }

    private void createVeilLight(ManagedLight managed) {
        try {
            if ("point".equals(managed.type)) {
                PointLight pointLight = new PointLight();
                pointLight.setPosition(managed.x, managed.y, managed.z)
                        .setColor((float) managed.r, (float) managed.g, (float) managed.b)
                        .setBrightness((float) managed.brightness)
                        .setRadius((float) managed.radius);

                VeilRenderSystem.renderer().getLightRenderer().addLight(pointLight);
                managed.veilLight = pointLight;

            } else if ("area".equals(managed.type)) {
                AreaLight areaLight = new AreaLight();
                areaLight.setPosition(managed.x, managed.y, managed.z)
                        .setColor((float) managed.r, (float) managed.g, (float) managed.b)
                        .setBrightness((float) managed.brightness)
                        .setSize((float) managed.width, (float) managed.height);

                VeilRenderSystem.renderer().getLightRenderer().addLight(areaLight);
                managed.veilLight = areaLight;
            }

            LOGGER.debug("Created Veil {} light for managed light {}", managed.type, managed.id);
        } catch (Exception e) {
            LOGGER.error("Failed to create Veil light for managed light {}", managed.id, e);
        }
    }

    private void updateActiveLight(ManagedLight managed) {
        if (managed.veilLight == null) return;

        try {
            if (managed.veilLight instanceof PointLight pointLight) {
                pointLight.setPosition(managed.x, managed.y, managed.z)
                        .setColor((float) managed.r, (float) managed.g, (float) managed.b)
                        .setBrightness((float) managed.brightness)
                        .setRadius((float) managed.radius);

            } else if (managed.veilLight instanceof AreaLight areaLight) {
                areaLight.setPosition(managed.x, managed.y, managed.z)
                        .setColor((float) managed.r, (float) managed.g, (float) managed.b)
                        .setBrightness((float) managed.brightness)
                        .setSize((float) managed.width, (float) managed.height);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to update Veil light {}", managed.id, e);
        }
    }

    private void destroyVeilLight(ManagedLight managed) {
        if (managed.veilLight != null) {
            try {
                VeilRenderSystem.renderer().getLightRenderer().removeLight(managed.veilLight);
                managed.veilLight = null;
                LOGGER.debug("Destroyed Veil light for managed light {}", managed.id);
            } catch (Exception e) {
                LOGGER.error("Failed to destroy Veil light {}", managed.id, e);
            }
        }
    }

    private boolean isPlayerWithinRange(double x, double y, double z) {
        PlayerEntity player = client.player;
        if (player == null) return false;

        Vec3d playerPos = player.getPos();
        double distance = playerPos.distanceTo(new Vec3d(x, y, z));
        return distance <= LIGHT_RENDER_DISTANCE;
    }

    public void cleanup() {
        clearAllLights();
        LOGGER.info("ClientLightingService cleaned up");
    }

    private static class ManagedLight {
        public final long id;
        public final String type;
        public double x, y, z;
        public float r, g, b;
        public float brightness;
        public float radius;
        public float width, height;
        public Light veilLight; // Change from Object to Light

        public ManagedLight(long id, String type, double x, double y, double z, float r, float g, float b, float brightness) {
            this.id = id;
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.r = r;
            this.g = g;
            this.b = b;
            this.brightness = brightness;
        }
    }
}