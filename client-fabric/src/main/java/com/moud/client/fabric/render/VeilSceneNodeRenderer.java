package com.moud.client.fabric.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;
import com.moud.client.fabric.player.PlayerBodyAttachmentCache;
import com.moud.client.fabric.render.mesh.MoudMeshBuffer;
import com.moud.client.fabric.render.MoudTextures;
import com.moud.client.fabric.render.picking.NodePickingPass;
import com.moud.client.fabric.render.picking.OutlineRenderer;
import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.client.fabric.render.veil.VeilDynamicShaders;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.DirectionalLightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.fabric.event.FabricVeilRenderLevelStageEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
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
    private static boolean gBuffersEnabled;

    private static long cachedVersion = Long.MIN_VALUE;
    private static long cachedSnapshotVersion = Long.MIN_VALUE;
    private static long cachedOverrideVersion = Long.MIN_VALUE;
    private static long cachedPhysicsVersion = Long.MIN_VALUE;
    private static long cachedResetVersion = Long.MIN_VALUE;
    private static List<SceneSnapshot.NodeSnapshot> cachedNodes = List.of();
    private static Map<Long, SceneSnapshot.NodeSnapshot> cachedNodesById = Map.of();
    private static final Map<Long, NodePoseState> poseStatesById = new HashMap<>();

    private static long poseFrameId;
    private static float poseFrameTickDelta;
    private static final Map<Long, CachedPose> worldPoseCacheById = new HashMap<>();

    private static long cachedLightsVersion = Long.MIN_VALUE;
    private static long cachedLightsOverrideVersion = Long.MIN_VALUE;
    private static final Map<Long, LightRenderHandle<?>> lightHandlesByNodeId = new HashMap<>();
    private static final Map<String, LightRenderHandle<?>> playerAttachLightHandles = new HashMap<>();

    private static final AtomicLong runtimeOverrideVersion = new AtomicLong();
    private static volatile long runtimeBodyNodeId;
    private static volatile Pose runtimeBodyWorldPose;
    private static volatile float runtimeBodyX;
    private static volatile float runtimeBodyY;
    private static volatile float runtimeBodyZ;
    private static volatile float runtimeBodyYawDeg;

    private static final MeshShaderRenderer meshShader = new MeshShaderRenderer();
    private static final InstancedBatchRenderer batchRenderer = new InstancedBatchRenderer(meshShader.sceneLights());
    private static final MultiMeshRenderer multiMeshRenderer = new MultiMeshRenderer(meshShader.sceneLights());
    private static final DecalRenderer decalRenderer = new DecalRenderer(meshShader);
    private static final NodePickingPass pickingPass = new NodePickingPass();
    private static final OutlineRenderer outlineRenderer = new OutlineRenderer();

    private static String activeAttachmentPlayerUuid = null;
    private static long activeAttachmentRootNodeId = 0L;
    private static Map<Long, Long> nodeToAttachAncestor = Map.of();
    private static List<SceneSnapshot.NodeSnapshot> playerAttachmentAllNodes = List.of();
    private static Map<Long, List<SceneSnapshot.NodeSnapshot>> attachmentDescendants = Map.of();
    private static List<SceneSnapshot.NodeSnapshot> filteredCachedNodes = List.of();
    private static final HashMap<Long, Pose> playerAttachPoseScratch = new HashMap<>();

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
        if (!gBuffersEnabled) {
            gBuffersEnabled = true;
            try {
                VeilRenderSystem.renderer().enableBuffers(
                        Identifier.of("moud", "pbr"),
                        DynamicBufferType.ALBEDO,
                        DynamicBufferType.NORMAL,
                        DynamicBufferType.DEBUG
                );
                VeilDynamicShaders.clear();
                int active = VeilRenderSystem.renderer().getActiveBuffers();
            } catch (Exception ignored) {
            }
        }

        float tickDelta = tickDelta(deltaTracker);
        if (stage == VeilRenderLevelStageEvent.Stage.AFTER_SKY) {
            syncLights(tickDelta);
        }
        if (stage == VeilRenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            if (bufferSource == null || camera == null) {
                return;
            }
            bufferSource.draw();
            renderMeshes(bufferSource, camera, frustumMatrix, projectionMatrix, tickDelta);
        }
    }

    private static void renderMeshes(VertexConsumerProvider.Immediate consumers,
                                     Camera camera,
                                     Matrix4fc frustumMatrix,
                                     Matrix4fc projectionMatrix,
                                     float tickDelta) {
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

        meshShader.collectLights(filteredCachedNodes, VeilSceneNodeRenderer::worldPose);
        if (!playerAttachmentAllNodes.isEmpty()) {
            Set<String> pbrLightUuids = PlayerBodyAttachmentCache.getActiveUuids();
            if (!pbrLightUuids.isEmpty()) {
                for (SceneSnapshot.NodeSnapshot attachNode : playerAttachmentAllNodes) {
                    List<SceneSnapshot.NodeSnapshot> desc = attachmentDescendants.get(attachNode.nodeId());
                    if (desc == null || desc.isEmpty()) continue;
                    for (String uuid : pbrLightUuids) {
                        activeAttachmentPlayerUuid = uuid;
                        activeAttachmentRootNodeId = attachNode.nodeId();
                        playerAttachPoseScratch.clear();
                        computePlayerAttachRootPose(attachNode.nodeId(), attachNode, uuid);
                        meshShader.collectLightsAdd(desc, VeilSceneNodeRenderer::worldPose);
                    }
                    activeAttachmentPlayerUuid = null;
                    activeAttachmentRootNodeId = 0L;
                }
            }
        }
        batchRenderer.renderBatched(filteredCachedNodes, VeilSceneNodeRenderer::worldPose, camPos, camera, frustumMatrix, projectionMatrix, client, tickDelta);
        multiMeshRenderer.renderAll(filteredCachedNodes, VeilSceneNodeRenderer::worldPose, camPos, camera, frustumMatrix, projectionMatrix, client, tickDelta);

        renderNodeListManual(filteredCachedNodes, consumers, matrices, camPos, camera, frustumMatrix, projectionMatrix, client, tickDelta);

        consumers.draw();
        decalRenderer.renderAll(filteredCachedNodes, VeilSceneNodeRenderer::worldPose, camPos, camera, frustumMatrix, projectionMatrix, client, tickDelta);

        if (!playerAttachmentAllNodes.isEmpty()) {
            Set<String> activeUuids = PlayerBodyAttachmentCache.getActiveUuids();
            if (!activeUuids.isEmpty()) {
                for (SceneSnapshot.NodeSnapshot attachNode : playerAttachmentAllNodes) {
                    List<SceneSnapshot.NodeSnapshot> descendants = attachmentDescendants.get(attachNode.nodeId());
                    if (descendants == null || descendants.isEmpty()) {
                        continue;
                    }
                    for (String uuid : activeUuids) {
                        activeAttachmentPlayerUuid = uuid;
                        activeAttachmentRootNodeId = attachNode.nodeId();
                        playerAttachPoseScratch.clear();
                        computePlayerAttachRootPose(attachNode.nodeId(), attachNode, uuid);

                        batchRenderer.renderBatched(descendants, VeilSceneNodeRenderer::worldPose, camPos, camera, frustumMatrix, projectionMatrix, client, tickDelta);
                        multiMeshRenderer.renderAll(descendants, VeilSceneNodeRenderer::worldPose, camPos, camera, frustumMatrix, projectionMatrix, client, tickDelta);
                        renderNodeListManual(descendants, consumers, matrices, camPos, camera, frustumMatrix, projectionMatrix, client, tickDelta);
                        consumers.draw();
                        decalRenderer.renderAll(descendants, VeilSceneNodeRenderer::worldPose, camPos, camera, frustumMatrix, projectionMatrix, client, tickDelta);
                        consumers.draw();
                    }
                    activeAttachmentPlayerUuid = null;
                    activeAttachmentRootNodeId = 0L;
                }
            }
        }

        EditorContext editorCtx = EditorOverlayBus.get();
        if (editorCtx != null && editorCtx.isActive()) {
            int[] viewport = new int[4];
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, viewport);
            int vpW = viewport[2];
            int vpH = viewport[3];

            if (vpW > 0 && vpH > 0 && editorCtx.isMouseInViewport()) {
                pickingPass.render(cachedNodes, VeilSceneNodeRenderer::worldPose,
                        camPos, frustumMatrix, projectionMatrix,
                        vpW, vpH,
                        editorCtx.mouseViewportNdcX(), editorCtx.mouseViewportNdcY());
                editorCtx.setHoveredNodeId(pickingPass.hoveredNodeId());
            }

            long hoveredId = editorCtx.hoveredNodeId();
            long selectedId = editorCtx.selectedNodeId();
            if (hoveredId > 0 || selectedId > 0) {
                outlineRenderer.render(cachedNodes, cachedNodesById,
                        VeilSceneNodeRenderer::worldPose,
                        camPos, frustumMatrix, projectionMatrix,
                        hoveredId, selectedId, client);
            }
        }
    }

    private static void renderUnitCube(VertexConsumer vc, MatrixStack.Entry entry, int light, int overlay, int r, int g, int b, int a) {
        if (vc == null || entry == null) {
            return;
        }
        quad(vc, entry,
                0, 0, 0, 0, 1,
                0, 1, 0, 0, 0,
                1, 1, 0, 1, 0,
                1, 0, 0, 1, 1,
                light, overlay,
                0, 0, -1, r, g, b, a);
        quad(vc, entry,
                0, 0, 1, 1, 1,
                1, 0, 1, 0, 1,
                1, 1, 1, 0, 0,
                0, 1, 1, 1, 0,
                light, overlay,
                0, 0, 1, r, g, b, a);
        quad(vc, entry,
                0, 0, 0, 1, 1,
                0, 0, 1, 0, 1,
                0, 1, 1, 0, 0,
                0, 1, 0, 1, 0,
                light, overlay,
                -1, 0, 0, r, g, b, a);
        quad(vc, entry,
                1, 0, 0, 0, 1,
                1, 1, 0, 0, 0,
                1, 1, 1, 1, 0,
                1, 0, 1, 1, 1,
                light, overlay,
                1, 0, 0, r, g, b, a);
        quad(vc, entry,
                0, 0, 0, 0, 0,
                1, 0, 0, 1, 0,
                1, 0, 1, 1, 1,
                0, 0, 1, 0, 1,
                light, overlay,
                0, -1, 0, r, g, b, a);
        quad(vc, entry,
                0, 1, 0, 0, 1,
                0, 1, 1, 0, 0,
                1, 1, 1, 1, 0,
                1, 1, 0, 1, 1,
                light, overlay,
                0, 1, 0, r, g, b, a);
    }

    private static void renderNodeListManual(
            List<SceneSnapshot.NodeSnapshot> nodes,
            VertexConsumerProvider.Immediate consumers,
            MatrixStack matrices,
            Vec3d camPos,
            Camera camera,
            Matrix4fc frustumMatrix,
            Matrix4fc projectionMatrix,
            MinecraftClient client,
            float tickDelta) {
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null) {
                continue;
            }
            if (!parseBool(stringProp(node, "visible"), true)) {
                continue;
            }
            String type = node.type();

            if ("Model3D".equals(type)) {
                Pose world = worldPose(node.nodeId());
                if (world == null) continue;
                int light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(world.pos.x, world.pos.y, world.pos.z));
                matrices.push();
                matrices.translate(world.pos.x - camPos.x, world.pos.y - camPos.y, world.pos.z - camPos.z);
                matrices.multiply(world.rot);
                matrices.scale(world.scale.x, world.scale.y, world.scale.z);
                Model3DRenderer.render(consumers, matrices, node, light);
                matrices.pop();
                continue;
            }

            if (!"MeshInstance3D".equals(type) && !"CSGBox".equals(type) && !"Sprite3D".equals(type)) {
                continue;
            }

            String materialPath = stringProp(node, "material");
            if (!"Sprite3D".equals(type) && (materialPath == null || materialPath.isBlank())) {
                String texProp = stringProp(node, "texture");
                boolean hasCustomTexture = texProp != null && !texProp.isBlank()
                        && !MoudTextures.WHITE_ID.toString().equals(texProp)
                        && !"moud:dynamic/white".equals(texProp);
                if (!hasCustomTexture) continue;
            }

            Pose world = worldPose(node.nodeId());
            if (world == null) {
                continue;
            }

            if (meshShader.renderNode(node, world, camPos, camera, frustumMatrix, projectionMatrix, client, tickDelta)) {
                continue;
            }

            if ("Sprite3D".equals(type)) {
                continue;
            }

            float tintR = clamp01(parseFloat(stringProp(node, "color_tint_r"), 1.0f));
            float tintG = clamp01(parseFloat(stringProp(node, "color_tint_g"), 1.0f));
            float tintB = clamp01(parseFloat(stringProp(node, "color_tint_b"), 1.0f));
            int tintRi = Math.round(tintR * 255.0f);
            int tintGi = Math.round(tintG * 255.0f);
            int tintBi = Math.round(tintB * 255.0f);

            float opacity = clamp01(parseFloat(stringProp(node, "opacity"), 1.0f));
            int alphaI = Math.round(opacity * 255.0f);

            Identifier textureId = meshShader.resolveNodeTexture(node);
            RenderLayer layer = alphaI < 255
                    ? RenderLayer.getEntityTranslucentCull(textureId)
                    : RenderLayer.getEntityCutout(textureId);
            VertexConsumer vc = consumers.getBuffer(layer);
            int light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(world.pos.x, world.pos.y, world.pos.z));

            matrices.push();
            matrices.translate(world.pos.x - camPos.x, world.pos.y - camPos.y, world.pos.z - camPos.z);
            matrices.multiply(world.rot);
            matrices.scale(world.scale.x, world.scale.y, world.scale.z);
            matrices.translate(-0.5, -0.5, -0.5);
            renderUnitCube(vc, matrices.peek(), light, OverlayTexture.DEFAULT_UV, tintRi, tintGi, tintBi, alphaI);
            matrices.pop();
        }
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

    public static void clearMaterialTextureCache() {
        meshShader.clear();
        batchRenderer.clear();
        multiMeshRenderer.clear();
        decalRenderer.clear();
        pickingPass.clear();
        outlineRenderer.clear();
        MoudMeshBuffer.cleanup();
        cachedVersion = Long.MIN_VALUE;
    }

    public static void clearLights() {
        if (lightHandlesByNodeId.isEmpty() && playerAttachLightHandles.isEmpty()) {
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
        for (LightRenderHandle<?> handle : playerAttachLightHandles.values()) {
            if (handle != null) {
                try {
                    handle.free();
                } catch (Exception ignored) {
                }
            }
        }
        playerAttachLightHandles.clear();
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

        HashSet<Long> aliveNormal = new HashSet<>();
        HashSet<String> alivePlayerAttach = new HashSet<>();

        for (SceneSnapshot.NodeSnapshot node : filteredCachedNodes) {
            if (node == null) continue;
            String type = node.type();
            if (!"OmniLight3D".equals(type) && !"DirectionalLight3D".equals(type) && !"SpotLight3D".equals(type)) continue;
            if (!parseBool(stringProp(node, "visible"), true)) continue;
            if (!parseBool(stringProp(node, "enabled"), true)) continue;

            Pose world = worldPose(node.nodeId());
            if (world == null) continue;

            long nodeId = node.nodeId();
            aliveNormal.add(nodeId);
            LightRenderHandle<?> handle = lightHandlesByNodeId.get(nodeId);
            handle = applyLightData(renderer, type, node, world, handle);
            if (handle != null) lightHandlesByNodeId.put(nodeId, handle);
        }

        if (!playerAttachmentAllNodes.isEmpty()) {
            Set<String> activeUuids = PlayerBodyAttachmentCache.getActiveUuids();
            if (!activeUuids.isEmpty()) {
                for (SceneSnapshot.NodeSnapshot attachNode : playerAttachmentAllNodes) {
                    List<SceneSnapshot.NodeSnapshot> descendants = attachmentDescendants.get(attachNode.nodeId());
                    if (descendants == null) continue;
                    for (SceneSnapshot.NodeSnapshot node : descendants) {
                        if (node == null) continue;
                        String type = node.type();
                        if (!"OmniLight3D".equals(type) && !"DirectionalLight3D".equals(type) && !"SpotLight3D".equals(type)) continue;
                        if (!parseBool(stringProp(node, "visible"), true)) continue;
                        if (!parseBool(stringProp(node, "enabled"), true)) continue;

                        for (String uuid : activeUuids) {
                            String key = node.nodeId() + ":" + uuid;
                            alivePlayerAttach.add(key);

                            activeAttachmentPlayerUuid = uuid;
                            activeAttachmentRootNodeId = attachNode.nodeId();
                            playerAttachPoseScratch.clear();
                            computePlayerAttachRootPose(attachNode.nodeId(), attachNode, uuid);
                            Pose world = worldPoseForPlayerAttach(node.nodeId(), uuid);
                            activeAttachmentPlayerUuid = null;
                            activeAttachmentRootNodeId = 0L;

                            LightRenderHandle<?> handle = playerAttachLightHandles.get(key);
                            handle = applyLightData(renderer, type, node, world, handle);
                            if (handle != null) playerAttachLightHandles.put(key, handle);
                        }
                    }
                }
            }
        }

        if (!lightHandlesByNodeId.isEmpty()) {
            Iterator<Map.Entry<Long, LightRenderHandle<?>>> it = lightHandlesByNodeId.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, LightRenderHandle<?>> e = it.next();
                if (!aliveNormal.contains(e.getKey())) {
                    freeHandle(e.getValue());
                    it.remove();
                }
            }
        }
        if (!playerAttachLightHandles.isEmpty()) {
            Iterator<Map.Entry<String, LightRenderHandle<?>>> it = playerAttachLightHandles.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, LightRenderHandle<?>> e = it.next();
                if (!alivePlayerAttach.contains(e.getKey())) {
                    freeHandle(e.getValue());
                    it.remove();
                }
            }
        }
    }

    private static LightRenderHandle<?> applyLightData(LightRenderer renderer, String type, SceneSnapshot.NodeSnapshot node, Pose world, LightRenderHandle<?> handle) {
        float colorR = clamp01(parseFloat(stringProp(node, "color_r"), 1.0f));
        float colorG = clamp01(parseFloat(stringProp(node, "color_g"), 1.0f));
        float colorB = clamp01(parseFloat(stringProp(node, "color_b"), 1.0f));
        float brightness = Math.max(0.0f, parseFloat(stringProp(node, "brightness"), 1.0f));

        if ("OmniLight3D".equals(type)) {
            handle = ensurePointLight(renderer, 0L, handle);
            if (handle == null) return null;
            float radius = Math.max(0.0f, parseFloat(stringProp(node, "radius"), 8.0f));
            ((PointLightData) handle.getLightData())
                    .setPosition(world.pos.x, world.pos.y, world.pos.z)
                    .setColor(colorR, colorG, colorB)
                    .setBrightness(brightness)
                    .setRadius(radius);
        } else if ("DirectionalLight3D".equals(type)) {
            handle = ensureDirectionalLight(renderer, 0L, handle);
            if (handle == null) return null;
            Vector3f dir = new Vector3f(0.0f, 0.0f, 1.0f);
            world.rot.transform(dir);
            if (dir.lengthSquared() > 1e-12f) dir.normalize();
            ((DirectionalLightData) handle.getLightData())
                    .setDirection(dir)
                    .setColor(colorR, colorG, colorB)
                    .setBrightness(brightness);
        } else {
            handle = ensureSpotLight(renderer, 0L, handle);
            if (handle == null) return null;
            float angleDeg = parseFloat(stringProp(node, "angle"), 45.0f);
            float distance = Math.max(0.0f, parseFloat(stringProp(node, "distance"), 10.0f));
            AreaLightData data = (AreaLightData) handle.getLightData();
            data.getPosition().set(world.pos.x, world.pos.y, world.pos.z);
            data.getOrientation().set(world.rot);
            data.setSize(0.1, 0.1)
                    .setAngle((float) Math.toRadians(angleDeg * 0.5f))
                    .setDistance(distance)
                    .setColor(colorR, colorG, colorB)
                    .setBrightness(brightness);
        }
        handle.markDirty();
        return handle;
    }

    private static void freeHandle(LightRenderHandle<?> handle) {
        if (handle != null) {
            try {
                handle.free();
            } catch (Exception ignored) {
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
        long physicsVersion = ClientSceneBus.physicsVersion();
        long resetVersion = ClientSceneBus.resetVersion();
        long overrideVersion = runtimeOverrideVersion.get();
        boolean sceneChanged = version != cachedVersion;
        boolean overrideChanged = overrideVersion != cachedOverrideVersion;
        if (!sceneChanged && !overrideChanged) {
            return;
        }
        cachedOverrideVersion = overrideVersion;
        if (sceneChanged) {
            boolean physicsChanged = physicsVersion != cachedPhysicsVersion;
            boolean isReset = resetVersion != cachedResetVersion;
            cachedVersion = version;
            cachedPhysicsVersion = physicsVersion;
            cachedResetVersion = resetVersion;
            cachedNodes = ClientSceneBus.copyNodes();
            HashMap<Long, SceneSnapshot.NodeSnapshot> next = new HashMap<>(Math.max(16, cachedNodes.size() * 2));
            for (SceneSnapshot.NodeSnapshot node : cachedNodes) {
                if (node == null) {
                    continue;
                }
                next.put(node.nodeId(), node);
            }
            cachedNodesById = next;
            buildPlayerAttachmentMaps(next);
            updatePoseStates(physicsChanged && !isReset);
            if (snapshotVersion != cachedSnapshotVersion) {
                meshShader.onSnapshotUpdate(cachedNodes);
                multiMeshRenderer.onSnapshotUpdate(cachedNodes);
            }
            cachedSnapshotVersion = snapshotVersion;
        }
    }

    private static void buildPlayerAttachmentMaps(Map<Long, SceneSnapshot.NodeSnapshot> nodesById) {
        HashMap<Long, List<Long>> children = new HashMap<>();
        for (SceneSnapshot.NodeSnapshot node : cachedNodes) {
            if (node == null) continue;
            long pid = node.parentId();
            if (pid > 0L) children.computeIfAbsent(pid, k -> new ArrayList<>()).add(node.nodeId());
        }

        List<SceneSnapshot.NodeSnapshot> attachAllList = new ArrayList<>();
        Map<Long, List<SceneSnapshot.NodeSnapshot>> descMap = new HashMap<>();
        Map<Long, Long> nodeToAttach = new HashMap<>();
        Set<Long> excludeIds = new HashSet<>();

        for (SceneSnapshot.NodeSnapshot node : cachedNodes) {
            if (node == null || !"PlayerAttachment".equals(node.type())) continue;
            String target = stringProp(node, "target");
            if (target != null && !target.isBlank() && !"all".equals(target)) continue;

            attachAllList.add(node);
            excludeIds.add(node.nodeId());

            List<SceneSnapshot.NodeSnapshot> descendants = new ArrayList<>();
            Queue<Long> queue = new ArrayDeque<>();
            List<Long> childIds = children.get(node.nodeId());
            if (childIds != null) queue.addAll(childIds);
            while (!queue.isEmpty()) {
                long cid = queue.poll();
                SceneSnapshot.NodeSnapshot cn = nodesById.get(cid);
                if (cn != null) {
                    descendants.add(cn);
                    nodeToAttach.put(cid, node.nodeId());
                    excludeIds.add(cid);
                }
                List<Long> gc = children.get(cid);
                if (gc != null) queue.addAll(gc);
            }
            descMap.put(node.nodeId(), descendants);
        }

        playerAttachmentAllNodes = attachAllList;
        attachmentDescendants = descMap;
        nodeToAttachAncestor = nodeToAttach;

        if (excludeIds.isEmpty()) {
            filteredCachedNodes = cachedNodes;
        } else {
            List<SceneSnapshot.NodeSnapshot> filtered = new ArrayList<>(cachedNodes.size());
            for (SceneSnapshot.NodeSnapshot node : cachedNodes) {
                if (node != null && !excludeIds.contains(node.nodeId())) filtered.add(node);
            }
            filteredCachedNodes = filtered;
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
                    Pose.interpolate(st.prevLocal, st.currLocal, poseFrameTickDelta, st.prevLocal);
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
                if (!st.initialized || st.parentId != parentId) {
                    Pose.copy(scratch, st.prevLocal);
                } else {
                    // Preserve the last authoritative pose as the interpolation start.
                    // This smooths replicated physics/snapshot updates without changing
                    // the actual simulated state.
                    Pose.copy(st.currLocal, st.prevLocal);
                }
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

        if (activeAttachmentPlayerUuid != null && nodeToAttachAncestor.containsKey(nodeId)) {
            return worldPoseForPlayerAttach(nodeId, activeAttachmentPlayerUuid);
        }

        CachedPose cached = worldPoseCacheById.get(nodeId);
        if (cached != null && cached.frame == poseFrameId) {
            return cached.pose;
        }

        SceneSnapshot.NodeSnapshot nodeSn = cachedNodesById.get(nodeId);

        if (nodeSn != null && "PlayerAttachment".equals(nodeSn.type())) {
            String target = stringProp(nodeSn, "target");
            if (target != null && !target.isBlank() && !"all".equals(target)) {
                return playerAttachmentPose(nodeId, nodeSn, target);
            }
            return Pose.IDENTITY;
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
            SceneSnapshot.NodeSnapshot parentSn = cachedNodesById.get(st.parentId);
            if (parentSn != null && "PlayerAttachment".equals(parentSn.type())) {
                String uuid = stringProp(parentSn, "target");
                if (uuid != null && !uuid.isBlank() && !"all".equals(uuid)) {
                    String attachPoint = nodeSn != null ? stringProp(nodeSn, "attachment_point") : null;
                    float[] attachPos = PlayerBodyAttachmentCache.getAttachPoint(uuid, attachPoint);
                    if (attachPos != null) {
                        float[] root = PlayerBodyAttachmentCache.getRoot(uuid);
                        out.pos.set(attachPos[0] + local.pos.x, attachPos[1] + local.pos.y, attachPos[2] + local.pos.z);
                        if (root != null) {
                            out.rot.set(quatFromEulerDeg(0f, -root[3], 0f)).mul(local.rot).normalize();
                        } else {
                            out.rot.set(local.rot);
                        }
                        out.scale.set(local.scale);
                        out.inherit = false;
                        return out;
                    }
                }
            }
            Pose parent = worldPose(st.parentId);
            Pose.compose(parent, local, out);
        } else {
            Pose.copy(local, out);
        }
        return out;
    }

    private static Pose playerAttachmentPose(long nodeId, SceneSnapshot.NodeSnapshot nodeSn, String playerUuid) {
        CachedPose cached = worldPoseCacheById.computeIfAbsent(nodeId, k -> new CachedPose());
        cached.frame = poseFrameId;
        Pose out = cached.pose;

        String attachPoint = stringProp(nodeSn, "attachment_point");
        boolean followRot = parseBool(stringProp(nodeSn, "follow_rotation"), false);
        float[] pos = PlayerBodyAttachmentCache.getAttachPoint(playerUuid, attachPoint);
        float[] root = PlayerBodyAttachmentCache.getRoot(playerUuid);

        if (pos != null) {
            out.pos.set(pos[0], pos[1], pos[2]);
            if (followRot && root != null) {
                out.rot.set(quatFromEulerDeg(0f, -root[3], 0f));
            } else {
                out.rot.identity();
            }
            out.scale.set(1f, 1f, 1f);
            out.inherit = false;
            return out;
        }

        NodePoseState st = poseStatesById.get(nodeId);
        if (st != null && st.initialized) {
            Pose.copy(st.interpolatedLocal(poseFrameId, poseFrameTickDelta), out);
        } else {
            Pose.copy(Pose.IDENTITY, out);
        }
        return out;
    }

    private static Pose worldPoseForPlayerAttach(long nodeId, String playerUuid) {
        Pose cached = playerAttachPoseScratch.get(nodeId);
        if (cached != null) return cached;

        SceneSnapshot.NodeSnapshot nodeSn = cachedNodesById.get(nodeId);

        if (nodeSn != null && "PlayerAttachment".equals(nodeSn.type())) {
            return computePlayerAttachRootPose(nodeId, nodeSn, playerUuid);
        }

        NodePoseState st = poseStatesById.get(nodeId);
        if (st == null || !st.initialized) return Pose.IDENTITY;
        Pose local = st.interpolatedLocal(poseFrameId, poseFrameTickDelta);

        SceneSnapshot.NodeSnapshot rootSn = activeAttachmentRootNodeId > 0L
                ? cachedNodesById.get(activeAttachmentRootNodeId) : null;
        boolean followRot = rootSn != null && parseBool(stringProp(rootSn, "follow_rotation"), false);
        boolean followAnim = nodeSn != null && parseBool(stringProp(nodeSn, "follow_animation"), false);

        String childAttachPoint = nodeSn != null ? stringProp(nodeSn, "attachment_point") : null;
        if (childAttachPoint != null && !childAttachPoint.isBlank()) {
            float[] pos = PlayerBodyAttachmentCache.getAttachPoint(playerUuid, childAttachPoint);
            float[] root = PlayerBodyAttachmentCache.getRoot(playerUuid);
            Pose out = new Pose();
            if (pos != null) {
                out.pos.set(pos[0] + local.pos.x, pos[1] + local.pos.y, pos[2] + local.pos.z);
                out.rot.set(boneWorldRot(playerUuid, childAttachPoint, local.rot, root, followRot, followAnim));
                out.scale.set(local.scale);
                out.inherit = false;
            } else {
                Pose.copy(local, out);
            }
            playerAttachPoseScratch.put(nodeId, out);
            return out;
        }

        Pose out = new Pose();
        if (local.inherit && st.parentId > 0L) {
            Pose parentPose = worldPoseForPlayerAttach(st.parentId, playerUuid);
            if (followAnim) {
                String rootAttachPoint = rootSn != null ? stringProp(rootSn, "attachment_point") : null;
                float[] root = PlayerBodyAttachmentCache.getRoot(playerUuid);
                Quaternionf animRot = boneWorldRot(playerUuid, rootAttachPoint, local.rot, root, true, true);
                out.pos.set(local.pos).mul(parentPose.scale);
                parentPose.rot.transform(out.pos);
                out.pos.add(parentPose.pos);
                out.rot.set(animRot);
                out.scale.set(parentPose.scale).mul(local.scale);
                out.inherit = local.inherit;
            } else {
                Pose.compose(parentPose, local, out);
            }
        } else {
            Pose.copy(local, out);
        }
        playerAttachPoseScratch.put(nodeId, out);
        return out;
    }

    private static Pose computePlayerAttachRootPose(long nodeId, SceneSnapshot.NodeSnapshot nodeSn, String playerUuid) {
        String attachPoint = stringProp(nodeSn, "attachment_point");
        boolean followRot = parseBool(stringProp(nodeSn, "follow_rotation"), false);
        float[] pos = PlayerBodyAttachmentCache.getAttachPoint(playerUuid, attachPoint);
        float[] root = PlayerBodyAttachmentCache.getRoot(playerUuid);

        Pose out = new Pose();
        if (pos != null) {
            out.pos.set(pos[0], pos[1], pos[2]);
            if (followRot && root != null) {
                out.rot.set(quatFromEulerDeg(0f, -root[3], 0f));
            } else {
                out.rot.identity();
            }
        } else {
            NodePoseState st = poseStatesById.get(nodeId);
            if (st != null && st.initialized) {
                Pose.copy(st.interpolatedLocal(poseFrameId, poseFrameTickDelta), out);
            } else {
                out.rot.identity();
            }
        }
        out.scale.set(1f, 1f, 1f);
        out.inherit = false;
        playerAttachPoseScratch.put(nodeId, out);
        return out;
    }

    private static Quaternionf boneWorldRot(String playerUuid, String attachPoint,
                                            Quaternionf localRot, float[] root,
                                            boolean followRot, boolean followAnim) {
        Quaternionf result = new Quaternionf();

        if (followAnim) {
            if (root != null) {
                result.set(quatFromEulerDeg(0f, -root[3], 0f));
            }
            float[] boneRot = PlayerBodyAttachmentCache.getRotation(playerUuid, attachPoint);
            if (boneRot != null) {
                result.mul(quatFromEulerDeg(boneRot[0], boneRot[1], boneRot[2]));
            }
        } else if (followRot && root != null) {
            result.set(quatFromEulerDeg(0f, -root[3], 0f));
        }

        return result.mul(localRot).normalize();
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

    public static float parseFloat(String value, float fallback) {
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

    public static boolean parseBool(String value, boolean fallback) {
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

    public static float clamp01(float v) {
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

    public static String stringProp(SceneSnapshot.NodeSnapshot node, String key) {
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

    public static final class Pose {
        static final Pose IDENTITY = new Pose(true);

        public final Vector3f pos = new Vector3f();
        public final Quaternionf rot = new Quaternionf();
        public final Vector3f scale = new Vector3f(1, 1, 1);
        public boolean inherit = true;

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
