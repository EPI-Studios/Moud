package com.moud.client.lighting;

import com.moud.api.math.Conversion;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.LightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientLightingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientLightingService.class);

    private static final ClientLightingService INSTANCE = new ClientLightingService();

    private static final float LIGHT_RENDER_DISTANCE_SQUARED = 16384.0F;
    private final Map<Long, ManagedLight> managedLights = new ConcurrentHashMap<>();
    private final MinecraftClient client;
    private long lastFrameTime = 0L;
    private static final Vector3f UP_VECTOR = new Vector3f(0.0F, 1.0F, 0.0F);
    private boolean initialized = false;

    private ClientLightingService() {
        this.client = MinecraftClient.getInstance();
    }

    public static ClientLightingService getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!initialized) {
            initialized = true;
            LOGGER.info("ClientLightingService initialized.");
        }
    }

    public void handleCreateOrUpdateLight(Map<String, Object> lightData) {
        if (!initialized) initialize();

        long id = Conversion.toLong(lightData.get("id"));
        ManagedLight light = managedLights.computeIfAbsent(id, (key) -> {
            String type = (String) lightData.get("type");
            LOGGER.info("Creating new managed light with ID: {} and type: {}", key, type);
            return new ManagedLight(key, type);
        });
        light.update(lightData);
        light.snap();
        LOGGER.debug("Updated light ID: {} with data: {}", id, lightData);
    }

    public void handleLightSync(Map<String, Object> data) {
        LOGGER.info("Syncing lights from server...");
        this.clearAllLights();
        List<Map<String, Object>> lights = (List<Map<String, Object>>) data.get("lights");
        if (lights != null) {
            LOGGER.info("Syncing {} lights", lights.size());
            for (Map<String, Object> lightData : lights) {
                handleCreateOrUpdateLight(lightData);
            }
        }
    }

    public void handleRemoveLight(long id) {
        ManagedLight managed = managedLights.remove(id);
        if (managed != null) {
            this.destroyVeilLight(managed);
            LOGGER.info("Removed light with ID: {}", id);
        }
    }

    private void clearAllLights() {
        LOGGER.info("Clearing all {} lights", managedLights.size());
        managedLights.values().forEach(this::destroyVeilLight);
        managedLights.clear();
    }

    public void tick() {
        if (this.client.player == null || !initialized) {

            lastFrameTime = 0L;
            return;
        }

        long currentTime = System.nanoTime();
        if (this.lastFrameTime == 0L) {
            this.lastFrameTime = currentTime;
            return;
        }

        float deltaTime = (float)(currentTime - this.lastFrameTime) / 1.0E9F;
        this.lastFrameTime = currentTime;
        Vec3d playerPos = this.client.player.getPos();

        for (ManagedLight light : managedLights.values()) {
            boolean shouldBeVisible = playerPos.squaredDistanceTo(light.targetPosition) < LIGHT_RENDER_DISTANCE_SQUARED;
            if (shouldBeVisible) {
                if (light.veilLightData == null) {
                    this.createVeilLight(light);
                }
                light.interpolate(deltaTime);
                this.updateVeilLight(light);
            } else if (light.veilLightData != null) {
                this.destroyVeilLight(light);
            }
        }
    }

    private void createVeilLight(ManagedLight managed) {
        try {
            if ("point".equals(managed.type)) {
                PointLightData pointLight = new PointLightData();
                managed.veilLightData = pointLight;
                this.updateVeilLight(managed);
                VeilRenderSystem.renderer().getLightRenderer().addLight(pointLight);
                LOGGER.info("Created Veil point light with ID: {}", managed.id);
            } else if ("area".equals(managed.type)) {
                AreaLightData areaLight = new AreaLightData();
                managed.veilLightData = areaLight;
                this.updateVeilLight(managed);
                VeilRenderSystem.renderer().getLightRenderer().addLight(areaLight);
                LOGGER.info("Created Veil area light with ID: {}", managed.id);
            } else {
                LOGGER.warn("Attempted to create a Veil light with an unknown type: {}", managed.type);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create Veil light of type '{}' with id {}", managed.type, managed.id, e);
        }
    }

    private void updateVeilLight(ManagedLight managed) {
        try {
            LightData lightData = managed.veilLightData;
            if (lightData instanceof PointLightData pointLight) {
                pointLight.setPosition((float)managed.interpolatedPosition.x, (float)managed.interpolatedPosition.y, (float)managed.interpolatedPosition.z)
                        .setColor(managed.r, managed.g, managed.b)
                        .setBrightness(managed.brightness)
                        .setRadius(managed.radius);
            } else if (lightData instanceof AreaLightData areaLight) {
                areaLight.getPosition().set((float)managed.interpolatedPosition.x, (float)managed.interpolatedPosition.y, (float)managed.interpolatedPosition.z);

                double dirLength = managed.interpolatedDirection.length();
                if (dirLength > 0.001) {
                    Vec3d normalizedDir = managed.interpolatedDirection.normalize();

                    Vector3f direction = new Vector3f(
                            (float)normalizedDir.x * -1.0F,
                            (float)normalizedDir.y * -1.0F,
                            (float)normalizedDir.z * -1.0F
                    );

                    Quaternionf tempOrientation = new Quaternionf();

                    float dotWithUp = direction.dot(UP_VECTOR);
                    if (Math.abs(Math.abs(dotWithUp) - 1.0f) < 0.001f) {
                        if (dotWithUp > 0) {
                            tempOrientation.rotationX((float)Math.PI);
                        }
                    } else {
                        Vector3f safeUp = new Vector3f(0.0F, 0.0F, 1.0F);
                        tempOrientation.lookAlong(direction, safeUp);
                    }

                    areaLight.getOrientation().set(tempOrientation);
                } else {
                    Quaternionf tempOrientation = new Quaternionf();
                    areaLight.getOrientation().set(tempOrientation);
                }

                areaLight.setColor(managed.r, managed.g, managed.b)
                        .setBrightness(managed.brightness)
                        .setSize((double)managed.width, (double)managed.height)
                        .setDistance(managed.distance)
                        .setAngle(managed.angle);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to update Veil light with ID: {}", managed.id, e);
        }
    }

    private void destroyVeilLight(ManagedLight managed) {
        if (managed.veilLightData != null) {
            try {
                LightRenderer lightRenderer = VeilRenderSystem.renderer().getLightRenderer();
                lightRenderer.getLights(managed.veilLightData.getType()).removeIf((handle) -> handle.getLightData() == managed.veilLightData);
                managed.veilLightData = null;
                LOGGER.debug("Destroyed Veil light with ID: {}", managed.id);
            } catch (Exception e) {
                LOGGER.error("Failed to destroy Veil light with ID: {}", managed.id, e);
            }
        }
    }

    public void cleanup() {
        this.client.execute(this::clearAllLights);

        LOGGER.info("ClientLightingService session cleaned up (lights cleared).");
    }

    private static class ManagedLight {
        final long id;
        final String type;
        LightData veilLightData;
        float r = 1.0f, g = 1.0f, b = 1.0f;
        float brightness = 1.0f, radius = 5.0f;
        float width = 1.0f, height = 1.0f, angle = 45.0f, distance = 10.0f;
        Vec3d interpolatedPosition, targetPosition;
        Vec3d interpolatedDirection, targetDirection;

        ManagedLight(long id, String type) {
            this.id = id;
            this.type = type;
            this.interpolatedPosition = Vec3d.ZERO;
            this.targetPosition = Vec3d.ZERO;
            this.interpolatedDirection = new Vec3d(0.0, -1.0, 0.0);
            this.targetDirection = new Vec3d(0.0, -1.0, 0.0);
        }

        void update(Map<String, Object> data) {
            this.r = Conversion.toFloat(data.getOrDefault("r", this.r));
            this.g = Conversion.toFloat(data.getOrDefault("g", this.g));
            this.b = Conversion.toFloat(data.getOrDefault("b", this.b));
            this.brightness = Conversion.toFloat(data.getOrDefault("brightness", this.brightness));
            this.angle = Conversion.toFloat(data.getOrDefault("angle", this.angle));
            this.distance = Conversion.toFloat(data.getOrDefault("distance", this.distance));
            LOGGER.info("Light {} received data: {}", this.id, data);
            LOGGER.info("Direction values - dirX: {}, dirY: {}, dirZ: {}",
                    data.get("dirX"), data.get("dirY"), data.get("dirZ"));
            if (data.containsKey("x")) {
                this.targetPosition = new Vec3d(
                        Conversion.toDouble(data.get("x")),
                        Conversion.toDouble(data.get("y")),
                        Conversion.toDouble(data.get("z"))
                );
            }

            if ("point".equals(this.type)) {
                this.radius = Conversion.toFloat(data.getOrDefault("radius", this.radius));
            } else if ("area".equals(this.type)) {
                this.width = Conversion.toFloat(data.getOrDefault("width", this.width));
                this.height = Conversion.toFloat(data.getOrDefault("height", this.height));
                if (data.containsKey("dirX")) {
                    Object dirXObj = data.get("dirX");
                    Object dirYObj = data.get("dirY");
                    Object dirZObj = data.get("dirZ");

                    LOGGER.info("Direction object types - dirX: {} ({}), dirY: {} ({}), dirZ: {} ({})",
                            dirXObj, dirXObj == null ? "null" : dirXObj.getClass().getName(),
                            dirYObj, dirYObj == null ? "null" : dirYObj.getClass().getName(),
                            dirZObj, dirZObj == null ? "null" : dirZObj.getClass().getName());

                    double dirX = Conversion.toDouble(dirXObj);
                    double dirY = Conversion.toDouble(dirYObj);
                    double dirZ = Conversion.toDouble(dirZObj);

                    LOGGER.info("After Conversion.toDouble - dirX: {}, dirY: {}, dirZ: {}", dirX, dirY, dirZ);

                    Vec3d rawDirection = new Vec3d(dirX, dirY, dirZ);
                    LOGGER.info("Vec3d created - x: {}, y: {}, z: {}, length: {}",
                            rawDirection.x, rawDirection.y, rawDirection.z, rawDirection.length());

                    double length = rawDirection.length();
                    if (length > 0.001) {
                        this.targetDirection = rawDirection.normalize();
                        LOGGER.info("Normalized direction: x: {}, y: {}, z: {}",
                                this.targetDirection.x, this.targetDirection.y, this.targetDirection.z);
                    } else {
                        LOGGER.warn("Zero-length direction for light {}, using default", this.id);
                        this.targetDirection = new Vec3d(0.0, -1.0, 0.0);
                    }
                }
            }

            if (this.veilLightData == null) {
                this.snap();
            }
        }

        void interpolate(float deltaTime) {
            float smoothing = 20.0F;
            float factor = 1.0F - (float)Math.exp((double)(-smoothing * deltaTime));
            this.interpolatedPosition = this.interpolatedPosition.lerp(this.targetPosition, (double)factor);
            if (this.targetDirection.lengthSquared() > 0.001D) {
                this.interpolatedDirection = this.interpolatedDirection.lerp(this.targetDirection, (double)factor);
                double length = this.interpolatedDirection.length();
                if (length > 0.001) {
                    this.interpolatedDirection = this.interpolatedDirection.normalize();
                }
            }
        }

        void snap() {
            this.interpolatedPosition = this.targetPosition;
            this.interpolatedDirection = this.targetDirection;
        }
    }
}