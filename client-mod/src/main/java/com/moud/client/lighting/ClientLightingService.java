package com.moud.client.lighting;

import com.moud.api.math.Conversion;
import com.moud.client.editor.runtime.RuntimeObjectRegistry;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.DirectionalLightData;
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
    private static final boolean DISABLE_CLIENT_SUN_LIGHT = false;
    private static final float MAX_LIGHT_CHECK_DISTANCE_SQUARED = 1048576.0F;
    private static final Vector3f UP_VECTOR = new Vector3f(0.0F, 1.0F, 0.0F);

    private final Map<Long, ManagedLight> managedLights = new ConcurrentHashMap<>();
    private final MinecraftClient client;
    private long lastFrameTime = 0L;
    private boolean initialized = false;
    private DirectionalLightData sunLight;

    private ClientLightingService() {
        this.client = MinecraftClient.getInstance();
    }

    public static ClientLightingService getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!initialized) {
            initialized = true;
            LOGGER.debug("ClientLightingService initialized.");
        }
    }

    public void handleCreateOrUpdateLight(Map<String, Object> lightData) {
        if (!initialized) initialize();

        long id = Conversion.toLong(lightData.get("id"));
        ManagedLight light = managedLights.computeIfAbsent(id, key ->
                new ManagedLight(key, (String) lightData.get("type")));
        light.update(lightData);
        light.snap();
        RuntimeObjectRegistry.getInstance().syncLight(id, lightData);
        LOGGER.trace("Updated light {}", id);
    }

    public void handleLightSync(Map<String, Object> data) {
        LOGGER.debug("Syncing lights from server...");
        clearAllLights();
        List<Map<String, Object>> lights = (List<Map<String, Object>>) data.get("lights");
        if (lights != null) {
            LOGGER.debug("Syncing {} lights", lights.size());
            lights.forEach(this::handleCreateOrUpdateLight);
        }
    }

    public void handleRemoveLight(long id) {
        ManagedLight managed = managedLights.remove(id);
        if (managed != null) {
            destroyVeilLight(managed);
            RuntimeObjectRegistry.getInstance().removeLight(id);
            LOGGER.debug("Removed light {}", id);
        }
    }

    private void clearAllLights() {
        LOGGER.debug("Clearing all {} lights", managedLights.size());
        managedLights.values().forEach(this::destroyVeilLight);
        managedLights.clear();
    }

    public void tick() {
        if (client.player == null || !initialized) {
            lastFrameTime = 0L;
            return;
        }

        long currentTime = System.nanoTime();
        if (lastFrameTime == 0L) {
            lastFrameTime = currentTime;
            return;
        }

        float deltaTime = (float)(currentTime - lastFrameTime) / 1.0E9F;
        lastFrameTime = currentTime;

        updateClientSunLight();
        Vec3d cameraPos = client.gameRenderer != null && client.gameRenderer.getCamera() != null
                ? client.gameRenderer.getCamera().getPos()
                : client.player.getPos();

        for (ManagedLight light : managedLights.values()) {
            double distanceSquared = cameraPos.squaredDistanceTo(light.targetPosition);
            boolean shouldBeVisible = distanceSquared < MAX_LIGHT_CHECK_DISTANCE_SQUARED;

            if (shouldBeVisible) {
                if (light.veilLightData == null) {
                    createVeilLight(light);
                }
                light.interpolate(deltaTime);
                updateVeilLight(light);
            } else if (light.veilLightData != null) {
                destroyVeilLight(light);
            }
        }
    }

    private void updateClientSunLight() {
        if (DISABLE_CLIENT_SUN_LIGHT || client.world == null || VeilRenderSystem.renderer() == null) {
            destroySunLightIfPresent();
            return;
        }

        if (sunLight == null) {
            try {
                sunLight = new DirectionalLightData();
                VeilRenderSystem.renderer().getLightRenderer().addLight(sunLight);
            } catch (Throwable t) {
                LOGGER.debug("Failed to create client sun light", t);
                sunLight = null;
                return;
            }
        }

        long timeOfDay = client.world.getTimeOfDay() % 24000L;
        float phase = ((timeOfDay - 6000.0f) / 24000.0f) * (float)(Math.PI * 2.0);

        float sinPhase = (float)Math.sin(phase);
        float cosPhase = (float)Math.cos(phase);

        Vector3f rayDir = new Vector3f(-sinPhase, -cosPhase, 0.0f);
        float len = rayDir.length();
        if (len > 1e-6f) {
            rayDir.div(len);
        } else {
            rayDir.set(0.0f, -1.0f, 0.0f);
        }

        float day = cosPhase * 0.5f + 0.5f;
        day = Math.max(0.0f, Math.min(1.0f, day));
        float sun = day * day;
        float moon = 1.0f - day;
        moon = moon * moon;

        float brightness = 1.10f * sun + 0.18f * moon;
        brightness = Math.max(0.0f, Math.min(1.35f, brightness));

        sunLight.setDirection(rayDir);
        sunLight.setColor(
                0.95f * sun + 0.55f * moon,
                0.98f * sun + 0.62f * moon,
                1.00f * sun + 0.95f * moon
        );
        sunLight.setBrightness(brightness);
    }

    private void destroySunLightIfPresent() {
        if (sunLight == null) return;

        try {
            LightRenderer lightRenderer = VeilRenderSystem.renderer().getLightRenderer();
            lightRenderer.getLights(sunLight.getType()).removeIf(handle -> handle.getLightData() == sunLight);
        } catch (Throwable ignored) {
        } finally {
            sunLight = null;
        }
    }

    private void createVeilLight(ManagedLight managed) {
        try {
            if ("point".equals(managed.type)) {
                PointLightData pointLight = new PointLightData();
                managed.veilLightData = pointLight;
                updateVeilLight(managed);
                VeilRenderSystem.renderer().getLightRenderer().addLight(pointLight);
                LOGGER.trace("Created Veil point light {}", managed.id);
            } else if ("area".equals(managed.type)) {
                AreaLightData areaLight = new AreaLightData();
                managed.veilLightData = areaLight;
                updateVeilLight(managed);
                VeilRenderSystem.renderer().getLightRenderer().addLight(areaLight);
                LOGGER.trace("Created Veil area light {}", managed.id);
            } else {
                LOGGER.warn("Unknown light type: {}", managed.type);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create Veil light of type '{}' with id {}", managed.type, managed.id, e);
        }
    }

    private void updateVeilLight(ManagedLight managed) {
        try {
            LightData lightData = managed.veilLightData;
            if (lightData instanceof PointLightData pointLight) {
                pointLight.setPosition(
                                (float)managed.interpolatedPosition.x,
                                (float)managed.interpolatedPosition.y,
                                (float)managed.interpolatedPosition.z
                        ).setColor(managed.r, managed.g, managed.b)
                        .setBrightness(managed.brightness)
                        .setRadius(managed.radius);
            } else if (lightData instanceof AreaLightData areaLight) {
                areaLight.getPosition().set(
                        (float)managed.interpolatedPosition.x,
                        (float)managed.interpolatedPosition.y,
                        (float)managed.interpolatedPosition.z
                );

                double dirLen = managed.interpolatedDirection.length();
                if (dirLen > 0.001) {
                    Vec3d norm = managed.interpolatedDirection.normalize();
                    Vector3f direction = new Vector3f((float)-norm.x, (float)-norm.y, (float)-norm.z);
                    Quaternionf orientation = new Quaternionf();

                    float dotWithUp = direction.dot(UP_VECTOR);
                    if (Math.abs(Math.abs(dotWithUp) - 1.0f) < 0.001f) {
                        if (dotWithUp > 0) {
                            orientation.rotationX((float)Math.PI);
                        }
                    } else {
                        orientation.lookAlong(direction, new Vector3f(0.0F, 0.0F, 1.0F));
                    }

                    areaLight.getOrientation().set(orientation);
                } else {
                    areaLight.getOrientation().set(new Quaternionf());
                }

                areaLight.setColor(managed.r, managed.g, managed.b)
                        .setBrightness(managed.brightness)
                        .setSize(managed.width, managed.height)
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
                lightRenderer.getLights(managed.veilLightData.getType())
                        .removeIf(handle -> handle.getLightData() == managed.veilLightData);
                managed.veilLightData = null;
                LOGGER.trace("Destroyed Veil light {}", managed.id);
            } catch (Exception e) {
                LOGGER.error("Failed to destroy Veil light with ID: {}", managed.id, e);
            }
        }
    }

    public void cleanup() {
        client.execute(this::clearAllLights);
        client.execute(this::destroySunLightIfPresent);
        LOGGER.debug("ClientLightingService session cleaned up (lights cleared).");
    }

    public ManagedLight getManagedLight(long id) {
        return managedLights.get(id);
    }

    public static class ManagedLight {
        public final long id;
        public final String type;
        public LightData veilLightData;
        public float r = 1.0f, g = 1.0f, b = 1.0f;
        public float brightness = 1.0f, radius = 5.0f;
        public float width = 1.0f, height = 1.0f, angle = 45.0f, distance = 10.0f;
        public Vec3d interpolatedPosition, targetPosition;
        public Vec3d interpolatedDirection, targetDirection;

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
            this.brightness = Conversion.toFloat(data.getOrDefault("brightness",
                    data.getOrDefault("intensity", this.brightness)));
            this.angle = Conversion.toFloat(data.getOrDefault("angle", this.angle));
            this.distance = Conversion.toFloat(data.getOrDefault("distance", this.distance));

            if (data.containsKey("x")) {
                this.targetPosition = new Vec3d(
                        Conversion.toDouble(data.get("x")),
                        Conversion.toDouble(data.get("y")),
                        Conversion.toDouble(data.get("z"))
                );
            }

            float range = Conversion.toFloat(data.getOrDefault("range", Float.NaN));
            if ("point".equals(this.type)) {
                float radiusFallback = Float.isNaN(range) ? this.radius : range;
                this.radius = Conversion.toFloat(data.getOrDefault("radius", radiusFallback));
            } else if ("area".equals(this.type)) {
                float distanceFallback = Float.isNaN(range) ? this.distance : range;
                this.width = Conversion.toFloat(data.getOrDefault("width", this.width));
                this.height = Conversion.toFloat(data.getOrDefault("height", this.height));
                this.distance = Conversion.toFloat(data.getOrDefault("distance", distanceFallback));

                if (data.containsKey("dirX")) {
                    double dirX = Conversion.toDouble(data.get("dirX"));
                    double dirY = Conversion.toDouble(data.get("dirY"));
                    double dirZ = Conversion.toDouble(data.get("dirZ"));
                    Vec3d rawDirection = new Vec3d(dirX, dirY, dirZ);

                    double length = rawDirection.length();
                    if (length > 0.001) {
                        this.targetDirection = rawDirection.normalize();
                    } else {
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
            float factor = 1.0F - (float)Math.exp(-smoothing * deltaTime);
            this.interpolatedPosition = this.interpolatedPosition.lerp(this.targetPosition, factor);

            if (this.targetDirection.lengthSquared() > 0.001) {
                this.interpolatedDirection = this.interpolatedDirection.lerp(this.targetDirection, factor);
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