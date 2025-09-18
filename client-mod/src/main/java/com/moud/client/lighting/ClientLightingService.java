package com.moud.client.lighting;

import com.moud.api.math.Conversion;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.LightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientLightingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientLightingService.class);
    private static final float LIGHT_RENDER_DISTANCE_SQUARED = 16384.0F;
    private final Map<Long, ClientLightingService.ManagedLight> managedLights = new ConcurrentHashMap();
    private final MinecraftClient client = MinecraftClient.getInstance();
    private long lastFrameTime = 0L;
    private static final Vector3f UP_VECTOR = new Vector3f(0.0F, 1.0F, 0.0F);

    public void handleCreateOrUpdateLight(Map<String, Object> lightData) {
        long id = Conversion.toLong(lightData.get("id"));
        ClientLightingService.ManagedLight light = (ClientLightingService.ManagedLight)this.managedLights.computeIfAbsent(id, (key) -> {
            return new ClientLightingService.ManagedLight(key, (String)lightData.get("type"));
        });
        light.update(lightData);
    }

    public void handleLightSync(Map<String, Object> data) {
        this.client.execute(() -> {
            this.clearAllLights();
            List<Map<String, Object>> lights = (List)data.get("lights");
            if (lights != null) {
                Iterator var3 = lights.iterator();

                while(var3.hasNext()) {
                    Map<String, Object> lightData = (Map)var3.next();
                    long id = Conversion.toLong(lightData.get("id"));
                    ClientLightingService.ManagedLight light = (ClientLightingService.ManagedLight)this.managedLights.computeIfAbsent(id, (key) -> {
                        return new ClientLightingService.ManagedLight(key, (String)lightData.get("type"));
                    });
                    light.update(lightData);
                    light.snap();
                }

            }
        });
    }

    public void handleRemoveLight(long id) {
        ClientLightingService.ManagedLight managed = (ClientLightingService.ManagedLight)this.managedLights.remove(id);
        if (managed != null) {
            this.client.execute(() -> {
                this.destroyVeilLight(managed);
            });
        }

    }

    private void clearAllLights() {
        this.managedLights.values().forEach(this::destroyVeilLight);
        this.managedLights.clear();
    }

    public void tick() {
        if (this.client.player != null) {
            long currentTime = System.nanoTime();
            if (this.lastFrameTime == 0L) {
                this.lastFrameTime = currentTime;
            } else {
                float deltaTime = (float)(currentTime - this.lastFrameTime) / 1.0E9F;
                this.lastFrameTime = currentTime;
                Vec3d playerPos = this.client.player.getPos();
                Iterator var5 = this.managedLights.values().iterator();

                while(var5.hasNext()) {
                    ClientLightingService.ManagedLight light = (ClientLightingService.ManagedLight)var5.next();
                    boolean shouldBeVisible = playerPos.squaredDistanceTo(light.targetPosition) < 16384.0D;
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
        }
    }

    private void createVeilLight(ClientLightingService.ManagedLight managed) {
        try {
            if ("point".equals(managed.type)) {
                PointLightData pointLight = new PointLightData();
                managed.veilLightData = pointLight;
                this.updateVeilLight(managed);
                VeilRenderSystem.renderer().getLightRenderer().addLight(pointLight);
            } else if ("area".equals(managed.type)) {
                AreaLightData areaLight = new AreaLightData();
                managed.veilLightData = areaLight;
                this.updateVeilLight(managed);
                VeilRenderSystem.renderer().getLightRenderer().addLight(areaLight);
            } else {
                LOGGER.warn("Attempted to create a Veil light with an unknown type: {}", managed.type);
            }
        } catch (Exception var3) {
            LOGGER.error("Failed to create Veil light of type '{}' with id {}", new Object[]{managed.type, managed.id, var3});
        }

    }

    private void updateVeilLight(ClientLightingService.ManagedLight managed) {
        LightData var4 = managed.veilLightData;
        if (var4 instanceof PointLightData) {
            PointLightData pointLight = (PointLightData)var4;
            pointLight.setPosition((float)managed.interpolatedPosition.x, (float)managed.interpolatedPosition.y, (float)managed.interpolatedPosition.z).setColor(managed.r, managed.g, managed.b).setBrightness(managed.brightness).setRadius(managed.radius);
        } else {
            var4 = managed.veilLightData;
            if (var4 instanceof AreaLightData) {
                AreaLightData areaLight = (AreaLightData)var4;
                areaLight.getPosition().set((float)managed.interpolatedPosition.x, (float)managed.interpolatedPosition.y, (float)managed.interpolatedPosition.z);
                Quaternionf tempOrientation = new Quaternionf();
                Vector3f direction = new Vector3f((float)managed.interpolatedDirection.x * -1.0F, (float)managed.interpolatedDirection.y * -1.0F, (float)managed.interpolatedDirection.z * -1.0F);
                tempOrientation.lookAlong(direction, UP_VECTOR);
                areaLight.getOrientation().set(tempOrientation);
                areaLight.setColor(managed.r, managed.g, managed.b).setBrightness(managed.brightness).setSize((double)managed.width, (double)managed.height).setDistance(managed.distance).setAngle(managed.angle);
            }
        }

    }

    private void destroyVeilLight(ClientLightingService.ManagedLight managed) {
        if (managed.veilLightData != null) {
            try {
                LightRenderer lightRenderer = VeilRenderSystem.renderer().getLightRenderer();
                lightRenderer.getLights(managed.veilLightData.getType()).removeIf((handle) -> {
                    return handle.getLightData() == managed.veilLightData;
                });
                managed.veilLightData = null;
            } catch (Exception var3) {
                LOGGER.error("Failed to destroy Veil light", var3);
            }
        }

    }

    public void cleanup() {
        this.client.execute(this::clearAllLights);
    }

    private static class ManagedLight {
        final long id;
        final String type;
        LightData veilLightData;
        float r;
        float g;
        float b;
        float brightness;
        float radius;
        float width;
        float height;
        float angle;
        float distance;
        Vec3d interpolatedPosition;
        Vec3d targetPosition;
        Vec3d interpolatedDirection;
        Vec3d targetDirection;

        ManagedLight(long id, String type) {
            this.interpolatedPosition = Vec3d.ZERO;
            this.targetPosition = Vec3d.ZERO;
            this.interpolatedDirection = new Vec3d(0.0D, 0.0D, 1.0D);
            this.targetDirection = new Vec3d(0.0D, 0.0D, 1.0D);
            this.id = id;
            this.type = type;
        }

        void update(Map<String, Object> data) {
            this.r = Conversion.toFloat(data.getOrDefault("r", this.r));
            this.g = Conversion.toFloat(data.getOrDefault("g", this.g));
            this.b = Conversion.toFloat(data.getOrDefault("b", this.b));
            this.brightness = Conversion.toFloat(data.getOrDefault("brightness", this.brightness));
            this.angle = Conversion.toFloat(data.getOrDefault("angle", this.angle));
            this.distance = Conversion.toFloat(data.getOrDefault("distance", this.distance));
            if (data.containsKey("x")) {
                this.targetPosition = new Vec3d(Conversion.toDouble(data.get("x")), Conversion.toDouble(data.get("y")), Conversion.toDouble(data.get("z")));
            }

            if ("point".equals(this.type)) {
                this.radius = Conversion.toFloat(data.getOrDefault("radius", this.radius));
            } else if ("area".equals(this.type)) {
                this.width = Conversion.toFloat(data.getOrDefault("width", this.width));
                this.height = Conversion.toFloat(data.getOrDefault("height", this.height));
                if (data.containsKey("dirX")) {
                    this.targetDirection = (new Vec3d(Conversion.toDouble(data.get("dirX")), Conversion.toDouble(data.get("dirY")), Conversion.toDouble(data.get("dirZ")))).normalize();
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
                this.interpolatedDirection = this.interpolatedDirection.lerp(this.targetDirection, (double)factor).normalize();
            }

        }

        void snap() {
            this.interpolatedPosition = this.targetPosition;
            this.interpolatedDirection = this.targetDirection;
        }
    }
}