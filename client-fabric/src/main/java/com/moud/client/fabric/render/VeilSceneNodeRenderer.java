package com.moud.client.fabric.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.fabric.assets.MoudTextAssets;
import com.moud.core.assets.ResPath;
import com.moud.client.fabric.render.material.MoudMaterial;
import com.moud.client.fabric.render.material.MoudMaterialParser;
import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.net.protocol.SceneSnapshot;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.DirectionalLightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.fabric.event.FabricVeilRenderLevelStageEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class VeilSceneNodeRenderer {
    private static boolean initialized;

    private static long cachedVersion = Long.MIN_VALUE;
    private static long cachedSnapshotVersion = Long.MIN_VALUE;
    private static long cachedOverrideVersion = Long.MIN_VALUE;
    private static List<SceneSnapshot.NodeSnapshot> cachedNodes = List.of();
    private static Map<Long, SceneSnapshot.NodeSnapshot> cachedNodesById = Map.of();
    private static final Map<Long, NodePoseState> poseStatesById = new HashMap<>();

    private static long poseFrameId;
    private static float poseFrameTickDelta;
    private static final Map<Long, CachedPose> worldPoseCacheById = new HashMap<>();

    private static long cachedLightsVersion = Long.MIN_VALUE;
    private static long cachedLightsOverrideVersion = Long.MIN_VALUE;
    private static final Map<Long, LightRenderHandle<?>> lightHandlesByNodeId = new HashMap<>();

    private static final AtomicLong runtimeOverrideVersion = new AtomicLong();
    private static volatile long runtimeBodyNodeId;
    private static volatile Pose runtimeBodyWorldPose;
    private static volatile float runtimeBodyX;
    private static volatile float runtimeBodyY;
    private static volatile float runtimeBodyZ;
    private static volatile float runtimeBodyYawDeg;

    private static final Object MATERIAL_TEX_LOCK = new Object();
    private static final HashMap<String, MaterialTexCache> textureByMaterialPath = new HashMap<>();

    private VeilSceneNodeRenderer() {
    }

    public static void setRuntimeBodyOverride(long nodeId, float x, float y, float z, float yawDeg) {
        if (nodeId <= 0L) {
            clearRuntimeBodyOverride();
            return;
        }
        float nx = Float.isFinite(x) ? x : 0.0f;
        float ny = Float.isFinite(y) ? y : 0.0f;
        float nz = Float.isFinite(z) ? z : 0.0f;
        float nYaw = Float.isFinite(yawDeg) ? yawDeg : 0.0f;

        if (runtimeBodyNodeId == nodeId
                && Math.abs(runtimeBodyX - nx) < 1e-5f
                && Math.abs(runtimeBodyY - ny) < 1e-5f
                && Math.abs(runtimeBodyZ - nz) < 1e-5f
                && Math.abs(runtimeBodyYawDeg - nYaw) < 1e-4f) {
            return;
        }

        Pose pose = new Pose();
        pose.pos.set(nx, ny, nz);
        pose.rot.set(quatFromEulerDeg(0.0f, nYaw, 0.0f));
        pose.scale.set(1.0f, 1.0f, 1.0f);
        pose.inherit = true;

        runtimeBodyNodeId = nodeId;
        runtimeBodyWorldPose = pose;
        runtimeBodyX = nx;
        runtimeBodyY = ny;
        runtimeBodyZ = nz;
        runtimeBodyYawDeg = nYaw;
        runtimeOverrideVersion.incrementAndGet();
    }

    public static void clearRuntimeBodyOverride() {
        if (runtimeBodyNodeId == 0L && runtimeBodyWorldPose == null) {
            return;
        }
        runtimeBodyNodeId = 0L;
        runtimeBodyWorldPose = null;
        runtimeBodyX = runtimeBodyY = runtimeBodyZ = runtimeBodyYawDeg = 0.0f;
        runtimeOverrideVersion.incrementAndGet();
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        FabricVeilRenderLevelStageEvent.EVENT.register(VeilSceneNodeRenderer::onRenderLevelStage);
    }

    private static void onRenderLevelStage(VeilRenderLevelStageEvent.Stage stage,
                                           WorldRenderer levelRenderer,
                                           VertexConsumerProvider.Immediate bufferSource,
                                           foundry.veil.api.client.render.MatrixStack matrixStack,
                                           Matrix4fc frustumMatrix,
                                           Matrix4fc projectionMatrix,
                                           int renderTick,
                                           RenderTickCounter deltaTracker,
                                           Camera camera,
                                           Frustum frustum) {
        float tickDelta = tickDelta(deltaTracker);
        if (stage == VeilRenderLevelStageEvent.Stage.AFTER_SKY) {
            syncLights(tickDelta);
        }
        if (stage == VeilRenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            if (bufferSource == null || camera == null) {
                return;
            }
            renderMeshes(bufferSource, camera, tickDelta);
        }
    }

    private static void renderMeshes(VertexConsumerProvider.Immediate consumers, Camera camera, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }

        refreshSceneCache();
        beginPoseFrame(tickDelta);
        if (cachedNodes.isEmpty()) {
            return;
        }

        Vec3d camPos = camera.getPos();
        MatrixStack matrices = new MatrixStack();

        for (SceneSnapshot.NodeSnapshot node : cachedNodes) {
            if (node == null) {
                continue;
            }
            if (!parseBool(stringProp(node, "visible"), true)) {
                continue;
            }
            String type = node.type();
            if (!"MeshInstance3D".equals(type) && !"CSGBox".equals(type)) {
                continue;
            }

            Pose world = worldPose(node.nodeId());
            if (world == null) {
                continue;
            }

            float tintR = clamp01(parseFloat(stringProp(node, "color_tint_r"), 1.0f));
            float tintG = clamp01(parseFloat(stringProp(node, "color_tint_g"), 1.0f));
            float tintB = clamp01(parseFloat(stringProp(node, "color_tint_b"), 1.0f));
            int tintRi = Math.round(tintR * 255.0f);
            int tintGi = Math.round(tintG * 255.0f);
            int tintBi = Math.round(tintB * 255.0f);

            Identifier textureId = resolveNodeTexture(node);
            VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutout(textureId));
            int light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(world.pos.x, world.pos.y, world.pos.z));

            matrices.push();
            matrices.translate(world.pos.x - camPos.x, world.pos.y - camPos.y, world.pos.z - camPos.z);
            matrices.multiply(world.rot);
            matrices.scale(world.scale.x, world.scale.y, world.scale.z);
            matrices.translate(-0.5, -0.5, -0.5);
            renderUnitCube(vc, matrices.peek(), light, OverlayTexture.DEFAULT_UV, tintRi, tintGi, tintBi, 255);
            matrices.pop();
        }
    }

    private static Identifier resolveNodeTexture(SceneSnapshot.NodeSnapshot node) {
        if (node == null) {
            return MoudTextures.WHITE_ID;
        }
        String materialPath = stringProp(node, "material");
        Identifier fromMaterial = resolveMaterialTexture(materialPath);
        if (fromMaterial != null) {
            return fromMaterial;
        }
        String textureRef = stringProp(node, "texture");
        return MoudTextures.resolve(textureRef);
    }

    private static Identifier resolveMaterialTexture(String materialPathRaw) {
        if (materialPathRaw == null || materialPathRaw.isBlank()) {
            return null;
        }
        String materialPath = materialPathRaw.trim();
        if (!materialPath.startsWith(ResPath.SCHEME)) {
            return null;
        }

        String txt = MoudTextAssets.readText(materialPath);
        if (txt == null) {
            return null;
        }

        synchronized (MATERIAL_TEX_LOCK) {
            MaterialTexCache cached = textureByMaterialPath.get(materialPath);
            if (cached != null && Objects.equals(cached.materialText, txt)) {
                return cached.textureId;
            }
        }

        Identifier textureId = null;
        try {
            MoudMaterial mat = MoudMaterialParser.parse(txt);
            if (mat != null && mat.params() != null) {
                for (MoudMaterial.Param p : mat.params().values()) {
                    if (p instanceof MoudMaterial.Param.Texture t) {
                        textureId = MoudTextures.resolve(t.textureRef());
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        synchronized (MATERIAL_TEX_LOCK) {
            textureByMaterialPath.put(materialPath, new MaterialTexCache(txt, textureId));
        }
        return textureId;
    }

    private record MaterialTexCache(String materialText, Identifier textureId) {
    }

    private static void renderUnitCube(VertexConsumer vc, MatrixStack.Entry entry, int light, int overlay, int r, int g, int b, int a) {
        if (vc == null || entry == null) {
            return;
        }
        // North (-Z)
        quad(vc, entry,
                0, 0, 0, 0, 1,
                0, 1, 0, 0, 0,
                1, 1, 0, 1, 0,
                1, 0, 0, 1, 1,
                light, overlay,
                0, 0, -1, r, g, b, a);
        // South (+Z)
        quad(vc, entry,
                0, 0, 1, 1, 1,
                1, 0, 1, 0, 1,
                1, 1, 1, 0, 0,
                0, 1, 1, 1, 0,
                light, overlay,
                0, 0, 1, r, g, b, a);
        // West (-X)
        quad(vc, entry,
                0, 0, 0, 1, 1,
                0, 0, 1, 0, 1,
                0, 1, 1, 0, 0,
                0, 1, 0, 1, 0,
                light, overlay,
                -1, 0, 0, r, g, b, a);
        // East (+X)
        quad(vc, entry,
                1, 0, 0, 0, 1,
                1, 1, 0, 0, 0,
                1, 1, 1, 1, 0,
                1, 0, 1, 1, 1,
                light, overlay,
                1, 0, 0, r, g, b, a);
        // Bottom (-Y)
        quad(vc, entry,
                0, 0, 0, 0, 0,
                1, 0, 0, 1, 0,
                1, 0, 1, 1, 1,
                0, 0, 1, 0, 1,
                light, overlay,
                0, -1, 0, r, g, b, a);
        // Top (+Y)
        quad(vc, entry,
                0, 1, 0, 0, 1,
                0, 1, 1, 0, 0,
                1, 1, 1, 1, 0,
                1, 1, 0, 1, 1,
                light, overlay,
                0, 1, 0, r, g, b, a);
    }

    private static void quad(VertexConsumer vc,
                             MatrixStack.Entry entry,
                             float x0, float y0, float z0, float u0, float v0,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             int light, int overlay,
                             float nx, float ny, float nz,
                             int r, int g, int b, int a) {
        vertex(vc, entry, x0, y0, z0, u0, v0, light, overlay, nx, ny, nz, r, g, b, a);
        vertex(vc, entry, x1, y1, z1, u1, v1, light, overlay, nx, ny, nz, r, g, b, a);
        vertex(vc, entry, x2, y2, z2, u2, v2, light, overlay, nx, ny, nz, r, g, b, a);
        vertex(vc, entry, x3, y3, z3, u3, v3, light, overlay, nx, ny, nz, r, g, b, a);
    }

    private static void vertex(VertexConsumer vc,
                               MatrixStack.Entry entry,
                               float x, float y, float z,
                               float u, float v,
                               int light, int overlay,
                               float nx, float ny, float nz,
                               int r, int g, int b, int a) {
        vc.vertex(entry, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(entry, nx, ny, nz);
    }

    public static void clearLights() {
        if (lightHandlesByNodeId.isEmpty()) {
            cachedLightsVersion = Long.MIN_VALUE;
            cachedLightsOverrideVersion = Long.MIN_VALUE;
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(VeilSceneNodeRenderer::clearLights);
            return;
        }
        for (LightRenderHandle<?> handle : lightHandlesByNodeId.values()) {
            if (handle != null) {
                try {
                    handle.free();
                } catch (Exception ignored) {
                }
            }
        }
        lightHandlesByNodeId.clear();
        cachedLightsVersion = Long.MIN_VALUE;
        cachedLightsOverrideVersion = Long.MIN_VALUE;
    }

    private static void syncLights(float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            clearLights();
            return;
        }

        refreshSceneCache();
        beginPoseFrame(tickDelta);
        cachedLightsVersion = cachedVersion;
        cachedLightsOverrideVersion = cachedOverrideVersion;

        LightRenderer renderer;
        try {
            renderer = VeilRenderSystem.renderer().getLightRenderer();
        } catch (Throwable t) {
            return;
        }

        HashSet<Long> alive = new HashSet<>();

        for (SceneSnapshot.NodeSnapshot node : cachedNodes) {
            if (node == null) {
                continue;
            }
            String type = node.type();
            if (!"OmniLight3D".equals(type) && !"DirectionalLight3D".equals(type) && !"SpotLight3D".equals(type)) {
                continue;
            }

            boolean enabled = parseBool(stringProp(node, "enabled"), true);
            if (!enabled) {
                continue;
            }

            Pose world = worldPose(node.nodeId());
            if (world == null) {
                continue;
            }

            float colorR = clamp01(parseFloat(stringProp(node, "color_r"), 1.0f));
            float colorG = clamp01(parseFloat(stringProp(node, "color_g"), 1.0f));
            float colorB = clamp01(parseFloat(stringProp(node, "color_b"), 1.0f));
            float brightness = Math.max(0.0f, parseFloat(stringProp(node, "brightness"), 1.0f));

            long nodeId = node.nodeId();
            alive.add(nodeId);

            LightRenderHandle<?> handle = lightHandlesByNodeId.get(nodeId);
            if ("OmniLight3D".equals(type)) {
                handle = ensurePointLight(renderer, nodeId, handle);
                if (handle == null) {
                    continue;
                }
                PointLightData data = (PointLightData) handle.getLightData();
                float radius = Math.max(0.0f, parseFloat(stringProp(node, "radius"), 8.0f));
                data.setPosition(world.pos.x, world.pos.y, world.pos.z)
                        .setColor(colorR, colorG, colorB)
                        .setBrightness(brightness)
                        .setRadius(radius);
                handle.markDirty();
            } else if ("DirectionalLight3D".equals(type)) {
                handle = ensureDirectionalLight(renderer, nodeId, handle);
                if (handle == null) {
                    continue;
                }
                DirectionalLightData data = (DirectionalLightData) handle.getLightData();
                Vector3f dir = new Vector3f(0.0f, 0.0f, 1.0f);
                world.rot.transform(dir);
                if (dir.lengthSquared() > 1e-12f) {
                    dir.normalize();
                }
                data.setDirection(dir)
                        .setColor(colorR, colorG, colorB)
                        .setBrightness(brightness);
                handle.markDirty();
            } else {
                handle = ensureSpotLight(renderer, nodeId, handle);
                if (handle == null) {
                    continue;
                }
                AreaLightData data = (AreaLightData) handle.getLightData();
                float angleDeg = parseFloat(stringProp(node, "angle"), 45.0f);
                float distance = Math.max(0.0f, parseFloat(stringProp(node, "distance"), 10.0f));

                data.getPosition().set(world.pos.x, world.pos.y, world.pos.z);
                data.getOrientation().set(world.rot);
                data.setSize(0.1, 0.1)
                        .setAngle((float) Math.toRadians(angleDeg))
                        .setDistance(distance)
                        .setColor(colorR, colorG, colorB)
                        .setBrightness(brightness);
                handle.markDirty();
            }

            lightHandlesByNodeId.put(nodeId, handle);
        }

        if (!lightHandlesByNodeId.isEmpty()) {
            Iterator<Map.Entry<Long, LightRenderHandle<?>>> it = lightHandlesByNodeId.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, LightRenderHandle<?>> e = it.next();
                if (!alive.contains(e.getKey())) {
                    LightRenderHandle<?> h = e.getValue();
                    if (h != null) {
                        try {
                            h.free();
                        } catch (Exception ignored) {
                        }
                    }
                    it.remove();
                }
            }
        }
    }

    private static LightRenderHandle<?> ensurePointLight(LightRenderer renderer, long nodeId, LightRenderHandle<?> existing) {
        if (renderer == null) {
            return null;
        }
        if (existing != null && existing.isValid() && existing.getLightData() instanceof PointLightData) {
            return existing;
        }
        if (existing != null) {
            try {
                existing.free();
            } catch (Exception ignored) {
            }
        }
        return renderer.addLight(new PointLightData());
    }

    private static LightRenderHandle<?> ensureDirectionalLight(LightRenderer renderer, long nodeId, LightRenderHandle<?> existing) {
        if (renderer == null) {
            return null;
        }
        if (existing != null && existing.isValid() && existing.getLightData() instanceof DirectionalLightData) {
            return existing;
        }
        if (existing != null) {
            try {
                existing.free();
            } catch (Exception ignored) {
            }
        }
        return renderer.addLight(new DirectionalLightData());
    }

    private static LightRenderHandle<?> ensureSpotLight(LightRenderer renderer, long nodeId, LightRenderHandle<?> existing) {
        if (renderer == null) {
            return null;
        }
        if (existing != null && existing.isValid() && existing.getLightData() instanceof AreaLightData) {
            return existing;
        }
        if (existing != null) {
            try {
                existing.free();
            } catch (Exception ignored) {
            }
        }
        return renderer.addLight(new AreaLightData());
    }

    private static void refreshSceneCache() {
        long version = ClientSceneBus.version();
        long snapshotVersion = ClientSceneBus.snapshotVersion();
        long overrideVersion = runtimeOverrideVersion.get();
        boolean sceneChanged = version != cachedVersion;
        boolean snapshotChanged = snapshotVersion != cachedSnapshotVersion;
        boolean overrideChanged = overrideVersion != cachedOverrideVersion;
        if (!sceneChanged && !overrideChanged) {
            return;
        }
        cachedOverrideVersion = overrideVersion;
        if (sceneChanged) {
            cachedVersion = version;
            cachedNodes = ClientSceneBus.copyNodes();
            HashMap<Long, SceneSnapshot.NodeSnapshot> next = new HashMap<>(Math.max(16, cachedNodes.size() * 2));
            for (SceneSnapshot.NodeSnapshot node : cachedNodes) {
                if (node == null) {
                    continue;
                }
                next.put(node.nodeId(), node);
            }
            cachedNodesById = next;
            updatePoseStates(snapshotChanged);
            cachedSnapshotVersion = snapshotVersion;
        }
    }

    private static void beginPoseFrame(float tickDelta) {
        poseFrameId++;
        poseFrameTickDelta = clamp01(tickDelta);
    }

    private static void updatePoseStates(boolean shiftPrev) {
        Pose scratch = new Pose();

        for (SceneSnapshot.NodeSnapshot node : cachedNodes) {
            if (node == null || node.nodeId() <= 0L) {
                continue;
            }
            NodePoseState st = poseStatesById.computeIfAbsent(node.nodeId(), ignored -> new NodePoseState());
            parseLocalPoseInto(node, scratch);
            long parentId = node.parentId();

            if (shiftPrev) {
                if (!st.initialized) {
                    Pose.copy(scratch, st.prevLocal);
                } else {
                    Pose.copy(st.currLocal, st.prevLocal);
                }
                Pose.copy(scratch, st.currLocal);
                st.parentId = parentId;
                st.initialized = true;
                st.invalidateInterp();
                continue;
            }

            boolean changed = !st.initialized
                    || st.parentId != parentId
                    || !Pose.approxEquals(st.currLocal, scratch);
            if (changed) {
                Pose.copy(scratch, st.prevLocal);
                Pose.copy(scratch, st.currLocal);
                st.parentId = parentId;
                st.initialized = true;
                st.invalidateInterp();
            }
        }

        if (!poseStatesById.isEmpty()) {
            Iterator<Map.Entry<Long, NodePoseState>> it = poseStatesById.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, NodePoseState> e = it.next();
                if (!cachedNodesById.containsKey(e.getKey())) {
                    it.remove();
                }
            }
        }
        if (!worldPoseCacheById.isEmpty()) {
            Iterator<Map.Entry<Long, CachedPose>> it = worldPoseCacheById.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, CachedPose> e = it.next();
                if (!cachedNodesById.containsKey(e.getKey())) {
                    it.remove();
                }
            }
        }
    }

    private static Pose worldPose(long nodeId) {
        if (nodeId <= 0L) {
            return Pose.IDENTITY;
        }
        Pose runtimeBody = runtimeBodyWorldPose;
        if (runtimeBody != null && nodeId == runtimeBodyNodeId) {
            return runtimeBody;
        }

        CachedPose cached = worldPoseCacheById.get(nodeId);
        if (cached != null && cached.frame == poseFrameId) {
            return cached.pose;
        }

        NodePoseState st = poseStatesById.get(nodeId);
        if (st == null || !st.initialized) {
            return Pose.IDENTITY;
        }
        Pose local = st.interpolatedLocal(poseFrameId, poseFrameTickDelta);

        if (cached == null) {
            cached = new CachedPose();
            worldPoseCacheById.put(nodeId, cached);
        }
        cached.frame = poseFrameId;
        Pose out = cached.pose;

        if (local.inherit && st.parentId > 0L) {
            Pose parent = worldPose(st.parentId);
            Pose.compose(parent, local, out);
        } else {
            Pose.copy(local, out);
        }
        return out;
    }

    private static void parseLocalPoseInto(SceneSnapshot.NodeSnapshot node, Pose out) {
        float x = 0.0f;
        float y = 0.0f;
        float z = 0.0f;
        float rxDeg = 0.0f;
        float ryDeg = 0.0f;
        float rzDeg = 0.0f;

        float sx = 1.0f;
        float sy = 1.0f;
        float sz = 1.0f;
        boolean hasScale = false;

        String inheritRaw = null;

        List<SceneSnapshot.Property> props = node.properties();
        if (props != null) {
            for (SceneSnapshot.Property p : props) {
                if (p == null || p.key() == null) {
                    continue;
                }
                String k = p.key();
                String v = p.value();
                switch (k) {
                    case "x" -> x = parseFloat(v, x);
                    case "y" -> y = parseFloat(v, y);
                    case "z" -> z = parseFloat(v, z);
                    case "rx" -> rxDeg = parseFloat(v, rxDeg);
                    case "ry" -> ryDeg = parseFloat(v, ryDeg);
                    case "rz" -> rzDeg = parseFloat(v, rzDeg);
                    case "sx" -> {
                        sx = parseFloat(v, sx);
                        hasScale = true;
                    }
                    case "sy" -> {
                        sy = parseFloat(v, sy);
                        hasScale = true;
                    }
                    case "sz" -> {
                        sz = parseFloat(v, sz);
                        hasScale = true;
                    }
                    case "@inherit_transform" -> inheritRaw = v;
                    default -> {
                    }
                }
            }
        }

        sx = safeScale(sx);
        sy = safeScale(sy);
        sz = safeScale(sz);

        boolean pivotIsMinCorner = "CSGBox".equals(node.type()) || "CSGBlock".equals(node.type());

        float px = x;
        float py = y;
        float pz = z;
        if (hasScale) {
            out.scale.set(sx, sy, sz);
        } else {
            out.scale.set(1.0f, 1.0f, 1.0f);
        }
        if (pivotIsMinCorner && hasScale) {
            px = x + sx * 0.5f;
            py = y + sy * 0.5f;
            pz = z + sz * 0.5f;
        }

        out.pos.set(px, py, pz);
        out.rot.set(quatFromEulerDeg(rxDeg, ryDeg, rzDeg));
        out.inherit = shouldInheritTransform(inheritRaw);
    }

    private static boolean shouldInheritTransform(String v) {
        if (v == null || v.isBlank()) {
            return true;
        }
        String s = v.trim().toLowerCase();
        return !("false".equals(s) || "0".equals(s));
    }

    private static Quaternionf quatFromEulerDeg(float rxDeg, float ryDeg, float rzDeg) {
        float rx = (float) Math.toRadians(Float.isFinite(rxDeg) ? rxDeg : 0.0f);
        float ry = (float) Math.toRadians(Float.isFinite(ryDeg) ? ryDeg : 0.0f);
        float rz = (float) Math.toRadians(Float.isFinite(rzDeg) ? rzDeg : 0.0f);
        return new Quaternionf().rotationZ(rz).mul(new Quaternionf().rotationY(ry)).mul(new Quaternionf().rotationX(rx)).normalize();
    }

    private static float parseFloat(String value, float fallback) {
        try {
            if (value == null) {
                return fallback;
            }
            float v = Float.parseFloat(value.trim());
            return Float.isFinite(v) ? v : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float safeScale(float value) {
        if (!Float.isFinite(value)) {
            return 1.0f;
        }
        float v = Math.abs(value) < 1e-6f ? 0.0f : value;
        return Math.max(1e-6f, v);
    }

    private static boolean parseBool(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        String v = value.trim().toLowerCase();
        if ("true".equals(v) || "1".equals(v) || "t".equals(v) || "yes".equals(v) || "y".equals(v)) {
            return true;
        }
        if ("false".equals(v) || "0".equals(v) || "f".equals(v) || "no".equals(v) || "n".equals(v)) {
            return false;
        }
        return fallback;
    }

    private static float clamp01(float v) {
        if (!Float.isFinite(v)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    private static float tickDelta(RenderTickCounter deltaTracker) {
        if (deltaTracker == null) {
            return 0.0f;
        }
        try {
            return deltaTracker.getTickDelta(true);
        } catch (Exception ignored) {
            return 0.0f;
        }
    }

    private static String stringProp(SceneSnapshot.NodeSnapshot node, String key) {
        if (node == null || key == null) {
            return null;
        }
        List<SceneSnapshot.Property> props = node.properties();
        if (props == null || props.isEmpty()) {
            return null;
        }
        for (SceneSnapshot.Property p : props) {
            if (p != null && key.equals(p.key())) {
                return p.value();
            }
        }
        return null;
    }

    private static final class NodePoseState {
        final Pose prevLocal = new Pose();
        final Pose currLocal = new Pose();
        final Pose interpLocal = new Pose();
        long parentId;
        boolean initialized;
        long interpFrame = Long.MIN_VALUE;

        Pose interpolatedLocal(long frameId, float t) {
            if (interpFrame != frameId) {
                Pose.interpolate(prevLocal, currLocal, t, interpLocal);
                interpFrame = frameId;
            }
            return interpLocal;
        }

        void invalidateInterp() {
            interpFrame = Long.MIN_VALUE;
        }
    }

    private static final class CachedPose {
        final Pose pose = new Pose();
        long frame = Long.MIN_VALUE;
    }

    private static final class Pose {
        static final Pose IDENTITY = new Pose(true);

        final Vector3f pos = new Vector3f();
        final Quaternionf rot = new Quaternionf();
        final Vector3f scale = new Vector3f(1, 1, 1);
        boolean inherit = true;

        Pose() {
        }

        Pose(boolean identity) {
            if (identity) {
                pos.set(0, 0, 0);
                rot.identity();
                scale.set(1, 1, 1);
                inherit = true;
            }
        }

        static void copy(Pose src, Pose dst) {
            if (src == null || dst == null) {
                return;
            }
            dst.pos.set(src.pos);
            dst.rot.set(src.rot);
            dst.scale.set(src.scale);
            dst.inherit = src.inherit;
        }

        static void interpolate(Pose a, Pose b, float t, Pose out) {
            if (a == null || b == null || out == null) {
                return;
            }
            float alpha = clamp01(t);
            out.pos.set(a.pos).lerp(b.pos, alpha);
            out.rot.set(a.rot).slerp(b.rot, alpha).normalize();
            out.scale.set(a.scale).lerp(b.scale, alpha);
            out.inherit = b.inherit;
        }

        static void compose(Pose parent, Pose child, Pose out) {
            if (child == null || out == null) {
                return;
            }
            if (parent == null) {
                copy(child, out);
                return;
            }
            out.pos.set(child.pos).mul(parent.scale);
            parent.rot.transform(out.pos);
            out.pos.add(parent.pos);
            out.rot.set(parent.rot).mul(child.rot).normalize();
            out.scale.set(parent.scale).mul(child.scale);
            out.inherit = child.inherit;
        }

        static boolean approxEquals(Pose a, Pose b) {
            if (a == b) {
                return true;
            }
            if (a == null || b == null) {
                return false;
            }
            if (a.inherit != b.inherit) {
                return false;
            }
            float epsPos = 1e-5f;
            if (Math.abs(a.pos.x - b.pos.x) > epsPos
                    || Math.abs(a.pos.y - b.pos.y) > epsPos
                    || Math.abs(a.pos.z - b.pos.z) > epsPos) {
                return false;
            }
            float epsScale = 1e-5f;
            if (Math.abs(a.scale.x - b.scale.x) > epsScale
                    || Math.abs(a.scale.y - b.scale.y) > epsScale
                    || Math.abs(a.scale.z - b.scale.z) > epsScale) {
                return false;
            }
            float epsRot = 1e-4f;
            return Math.abs(a.rot.x - b.rot.x) <= epsRot
                    && Math.abs(a.rot.y - b.rot.y) <= epsRot
                    && Math.abs(a.rot.z - b.rot.z) <= epsRot
                    && Math.abs(a.rot.w - b.rot.w) <= epsRot;
        }
    }
}
