package com.moud.client.fabric.render.scene.light;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.fabric.render.scene.math.Pose;
import com.moud.client.fabric.render.scene.state.SceneCacheManager;
import com.moud.client.fabric.render.scene.state.TransformManager;
import com.moud.client.fabric.render.scene.util.NodePropertyUtils;
import com.moud.net.protocol.SceneSnapshot;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.DirectionalLightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import org.joml.Vector3f;

public final class SceneLightManager {
    private final SceneCacheManager cacheManager;
    private final TransformManager transformManager;
    private final Map<Long, LightRenderHandle<?>> lightHandlesByNodeId = new HashMap<>();

    public SceneLightManager(SceneCacheManager cacheManager, TransformManager transformManager) {
        this.cacheManager = cacheManager;
        this.transformManager = transformManager;
    }

    public void clearLights() {
        if (lightHandlesByNodeId.isEmpty()) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(this::clearLights);
            return;
        }
        for (LightRenderHandle<?> handle : lightHandlesByNodeId.values()) {
            freeHandle(handle);
        }
        lightHandlesByNodeId.clear();
    }

    public void syncLights(float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            clearLights();
            return;
        }

        LightRenderer renderer;
        try {
            renderer = VeilRenderSystem.renderer().getLightRenderer();
        } catch (Throwable ignored) {
            return;
        }

        HashSet<Long> aliveNormal = new HashSet<>();

        for (SceneSnapshot.NodeSnapshot node : cacheManager.filteredCachedNodes()) {
            if (node == null || !isSupportedLight(node.type())) {
                continue;
            }
            if (!NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "visible"), true)
                    || !NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "enabled"), true)) {
                continue;
            }

            Pose world = transformManager.worldPose(node.nodeId());
            long nodeId = node.nodeId();
            aliveNormal.add(nodeId);
            LightRenderHandle<?> handle = applyLightData(renderer, node.type(), node, world, lightHandlesByNodeId.get(nodeId));
            if (handle != null) {
                lightHandlesByNodeId.put(nodeId, handle);
            }
        }

        Iterator<Map.Entry<Long, LightRenderHandle<?>>> normalIt = lightHandlesByNodeId.entrySet().iterator();
        while (normalIt.hasNext()) {
            Map.Entry<Long, LightRenderHandle<?>> entry = normalIt.next();
            if (!aliveNormal.contains(entry.getKey())) {
                freeHandle(entry.getValue());
                normalIt.remove();
            }
        }
    }

    private boolean isSupportedLight(String type) {
        return "OmniLight3D".equals(type) || "DirectionalLight3D".equals(type) || "SpotLight3D".equals(type);
    }

    private LightRenderHandle<?> applyLightData(LightRenderer renderer, String type, SceneSnapshot.NodeSnapshot node,
                                                Pose world, LightRenderHandle<?> handle) {
        float colorR = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_r"), 1.0f));
        float colorG = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_g"), 1.0f));
        float colorB = NodePropertyUtils.clamp01(NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "color_b"), 1.0f));
        float brightness = Math.max(0.0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "brightness"), 1.0f));

        boolean occluded = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "occluded"), false);

        if ("OmniLight3D".equals(type)) {
            handle = ensurePointLight(renderer, handle);
            if (handle == null) {
                return null;
            }
            float radius = Math.max(0.0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "radius"), 8.0f));
            ((PointLightData) handle.getLightData())
                    .setPosition(world.pos.x, world.pos.y, world.pos.z)
                    .setColor(colorR, colorG, colorB)
                    .setBrightness(brightness)
                    .setRadius(radius)
                    .setOcclusionEnabled(occluded);
        } else if ("DirectionalLight3D".equals(type)) {
            handle = ensureDirectionalLight(renderer, handle);
            if (handle == null) {
                return null;
            }
            Vector3f direction = new Vector3f(0.0f, 0.0f, 1.0f);
            world.rot.transform(direction);
            if (direction.lengthSquared() > 1e-12f) {
                direction.normalize();
            }
            ((DirectionalLightData) handle.getLightData())
                    .setDirection(direction)
                    .setColor(colorR, colorG, colorB)
                    .setBrightness(brightness);
        } else {
            handle = ensureSpotLight(renderer, handle);
            if (handle == null) {
                return null;
            }
            float angleDeg = NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "angle"), 45.0f);
            float distance = Math.max(0.0f, NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "distance"), 10.0f));
            AreaLightData data = (AreaLightData) handle.getLightData();
            data.getPosition().set(world.pos.x, world.pos.y, world.pos.z);
            data.getOrientation().set(world.rot);
            data.setSize(0.1, 0.1)
                    .setAngle((float) Math.toRadians(angleDeg * 0.5f))
                    .setDistance(distance)
                    .setColor(colorR, colorG, colorB)
                    .setBrightness(brightness)
                    .setOcclusionEnabled(occluded);
        }

        handle.markDirty();
        return handle;
    }

    private LightRenderHandle<?> ensurePointLight(LightRenderer renderer, LightRenderHandle<?> existing) {
        if (renderer == null) {
            return null;
        }
        if (existing != null && existing.isValid() && existing.getLightData() instanceof PointLightData) {
            return existing;
        }
        freeHandle(existing);
        return renderer.addLight(new PointLightData());
    }

    private LightRenderHandle<?> ensureDirectionalLight(LightRenderer renderer, LightRenderHandle<?> existing) {
        if (renderer == null) {
            return null;
        }
        if (existing != null && existing.isValid() && existing.getLightData() instanceof DirectionalLightData) {
            return existing;
        }
        freeHandle(existing);
        return renderer.addLight(new DirectionalLightData());
    }

    private LightRenderHandle<?> ensureSpotLight(LightRenderer renderer, LightRenderHandle<?> existing) {
        if (renderer == null) {
            return null;
        }
        if (existing != null && existing.isValid() && existing.getLightData() instanceof AreaLightData) {
            return existing;
        }
        freeHandle(existing);
        return renderer.addLight(new AreaLightData());
    }

    private void freeHandle(LightRenderHandle<?> handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.free();
        } catch (Exception ignored) {
        }
    }
}
