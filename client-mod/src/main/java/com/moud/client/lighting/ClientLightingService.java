package com.moud.client.lighting;

import com.moud.api.math.Conversion;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.AreaLight;
import foundry.veil.api.client.render.light.Light;
import foundry.veil.api.client.render.light.PointLight;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientLightingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientLightingService.class);
    private static final float LIGHT_RENDER_DISTANCE_SQUARED = 128.0f * 128.0f;

    private final Map<Long, ManagedLight> managedLights = new ConcurrentHashMap<>();
    private final MinecraftClient client;
    private long lastFrameTime = 0;

    public ClientLightingService() {
        this.client = MinecraftClient.getInstance();
    }

    public void handleCreateOrUpdateLight(Map<String, Object> lightData) {
        long id = Conversion.toLong(lightData.get("id"));
        ManagedLight light = managedLights.computeIfAbsent(id, key -> new ManagedLight(key, (String)lightData.get("type")));
        light.update(lightData);
    }

    public void handleLightSync(Map<String, Object> data) {
        client.execute(() -> {
            clearAllLights();
            List<Map<String, Object>> lights = (List<Map<String, Object>>) data.get("lights");
            if (lights == null) return;

            for (Map<String, Object> lightData : lights) {
                long id = Conversion.toLong(lightData.get("id"));
                ManagedLight light = managedLights.computeIfAbsent(id, key -> new ManagedLight(key, (String)lightData.get("type")));
                light.update(lightData);
                light.snap();
            }
        });
    }

    public void handleRemoveLight(long id) {
        ManagedLight managed = managedLights.remove(id);
        if (managed != null) {

            client.execute(() -> destroyVeilLight(managed));
        }
    }

    private void clearAllLights() {

        managedLights.values().forEach(this::destroyVeilLight);
        managedLights.clear();
    }

    public void tick() {
        if (client.player == null) return;

        long currentTime = System.nanoTime();
        if (lastFrameTime == 0) {
            lastFrameTime = currentTime;
            return;
        }
        float deltaTime = (currentTime - lastFrameTime) / 1_000_000_000.0f;
        lastFrameTime = currentTime;

        Vec3d playerPos = client.player.getPos();

        for (ManagedLight light : managedLights.values()) {
            boolean shouldBeVisible = playerPos.squaredDistanceTo(light.targetPosition) < LIGHT_RENDER_DISTANCE_SQUARED;

            if (shouldBeVisible) {
                if (light.veilLight == null) {
                    createVeilLight(light);
                }
                light.interpolate(deltaTime);
                updateVeilLight(light);
            } else if (light.veilLight != null) {
                destroyVeilLight(light);
            }
        }
    }

    private void createVeilLight(ManagedLight managed) {
        if ("point".equals(managed.type)) {
            managed.veilLight = new PointLight();
        } else if ("area".equals(managed.type)) {
            managed.veilLight = new AreaLight();
        }

        if (managed.veilLight != null) {
            VeilRenderSystem.renderer().getLightRenderer().addLight(managed.veilLight);
        }
    }

    private void updateVeilLight(ManagedLight managed) {
        if (managed.veilLight instanceof PointLight pointLight) {
            pointLight.setPosition(managed.interpolatedPosition.x, managed.interpolatedPosition.y, managed.interpolatedPosition.z)
                    .setColor(managed.r, managed.g, managed.b)
                    .setBrightness(managed.brightness)
                    .setRadius(managed.radius);
        } else if (managed.veilLight instanceof AreaLight areaLight) {
            Quaternionf orientation = new Quaternionf().lookAlong(
                    (float)managed.interpolatedDirection.x * -1.0f,
                    (float)managed.interpolatedDirection.y * -1.0f,
                    (float)managed.interpolatedDirection.z * -1.0f,
                    0, 1, 0
            );

            areaLight.setPosition(managed.interpolatedPosition.x, managed.interpolatedPosition.y, managed.interpolatedPosition.z)
                    .setOrientation(orientation)
                    .setColor(managed.r, managed.g, managed.b)
                    .setBrightness(managed.brightness)
                    .setSize(managed.width, managed.height);

            if (managed.angle > 0) areaLight.setAngle(managed.angle);
            if (managed.distance > 0) areaLight.setDistance(managed.distance);
        }
    }

    private void destroyVeilLight(ManagedLight managed) {
        if (managed.veilLight != null) {
            VeilRenderSystem.renderer().getLightRenderer().removeLight(managed.veilLight);
            managed.veilLight = null;
        }
    }

    public void cleanup() {
        client.execute(this::clearAllLights);
    }

    private static class ManagedLight {
        final long id;
        final String type;
        Light veilLight;

        float r, g, b, brightness, radius, width, height, angle, distance;
        Vec3d interpolatedPosition = Vec3d.ZERO;
        Vec3d targetPosition = Vec3d.ZERO;
        Vec3d interpolatedDirection = new Vec3d(0, 0, 1);
        Vec3d targetDirection = new Vec3d(0, 0, 1);

        ManagedLight(long id, String type) { this.id = id; this.type = type; }

        void update(Map<String, Object> data) {
            r = Conversion.toFloat(data.getOrDefault("r", r));
            g = Conversion.toFloat(data.getOrDefault("g", g));
            b = Conversion.toFloat(data.getOrDefault("b", b));
            brightness = Conversion.toFloat(data.getOrDefault("brightness", brightness));
            angle = Conversion.toFloat(data.getOrDefault("angle", angle));
            distance = Conversion.toFloat(data.getOrDefault("distance", distance));

            if (data.containsKey("x")) {
                targetPosition = new Vec3d(Conversion.toDouble(data.get("x")), Conversion.toDouble(data.get("y")), Conversion.toDouble(data.get("z")));
            }

            if ("point".equals(type)) {
                radius = Conversion.toFloat(data.getOrDefault("radius", radius));
            } else if ("area".equals(type)) {
                width = Conversion.toFloat(data.getOrDefault("width", width));
                height = Conversion.toFloat(data.getOrDefault("height", height));
                if (data.containsKey("dirX")) {
                    targetDirection = new Vec3d(Conversion.toDouble(data.get("dirX")), Conversion.toDouble(data.get("dirY")), Conversion.toDouble(data.get("dirZ"))).normalize();
                }
            }
        }

        void interpolate(float deltaTime) {
            float smoothing = 20.0f;
            float factor = 1.0f - (float)Math.exp(-smoothing * deltaTime);

            interpolatedPosition = interpolatedPosition.lerp(targetPosition, factor);
            if (targetDirection.lengthSquared() > 0.001) {
                interpolatedDirection = interpolatedDirection.lerp(targetDirection, factor).normalize();
            }
        }

        void snap() {
            interpolatedPosition = targetPosition;
            interpolatedDirection = targetDirection;
        }
    }
}