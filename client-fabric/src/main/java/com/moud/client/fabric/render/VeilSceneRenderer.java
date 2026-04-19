package com.moud.client.fabric.render;

import com.moud.client.fabric.editor.diagnostics.ClientFrameProfiler;
import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;
import com.moud.client.fabric.render.picking.NodePickingPass;
import com.moud.client.fabric.render.picking.OutlineRenderer;
import com.moud.client.fabric.render.scene.light.SceneLightManager;
import com.moud.client.fabric.render.scene.math.Pose;
import com.moud.client.fabric.render.scene.state.SceneCacheManager;
import com.moud.client.fabric.render.scene.state.TransformManager;
import com.moud.client.fabric.render.scene.subrender.FallbackMeshRenderer;
import com.moud.client.fabric.render.scene.subrender.particle.ParticleRenderer;
import com.moud.client.fabric.render.scene.subrender.SceneDebugRenderer;
import com.moud.client.fabric.render.scene.subrender.Text3DRenderer;
import com.moud.client.fabric.render.scene.subrender.ViewmodelRenderer;
import com.moud.client.fabric.render.scene.util.NodePropertyUtils;
import com.moud.core.physics.CollisionGeometry;
import com.moud.net.protocol.CollisionGeometrySnapshot;
import com.moud.net.protocol.SceneSnapshot;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.fabric.event.FabricVeilRenderLevelStageEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4fc;

public final class VeilSceneRenderer {
    private static boolean initialized;
    private static boolean gBuffersEnabled;
    private static boolean collisionDebugEnabled;

    private static final Map<Long, List<CollisionGeometry>> collisionGeometryCache = new ConcurrentHashMap<>();

    private static final SceneCacheManager cacheManager = new SceneCacheManager();
    private static final TransformManager transformManager = new TransformManager(cacheManager);
    private static final SceneLightManager lightManager = new SceneLightManager(cacheManager, transformManager);
    private static final Text3DRenderer textRenderer = new Text3DRenderer();
    private static final FallbackMeshRenderer fallbackRenderer = new FallbackMeshRenderer();
    private static final ParticleRenderer particleRenderer = new ParticleRenderer();
    private static final ViewmodelRenderer viewmodelRenderer = new ViewmodelRenderer();
    private static final SceneDebugRenderer debugRenderer = new SceneDebugRenderer();

    private static final MeshShaderRenderer meshShader = new MeshShaderRenderer();
    private static final InstancedBatchRenderer batchRenderer = new InstancedBatchRenderer(meshShader.sceneLights());
    private static final MultiMeshRenderer multiMeshRenderer = new MultiMeshRenderer(meshShader.sceneLights());
    private static final DecalRenderer decalRenderer = new DecalRenderer(meshShader);
    private static final NodePickingPass pickingPass = new NodePickingPass();
    private static final OutlineRenderer outlineRenderer = new OutlineRenderer();

    private VeilSceneRenderer() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        FabricVeilRenderLevelStageEvent.EVENT.register(VeilSceneRenderer::onRenderLevelStage);
    }

    public static void toggleCollisionDebug() {
        collisionDebugEnabled = !collisionDebugEnabled;
    }

    public static void onCollisionGeometry(CollisionGeometrySnapshot snapshot) {
        if (snapshot == null || snapshot.hulls() == null || snapshot.hulls().isEmpty()) {
            return;
        }
        collisionGeometryCache.put(snapshot.nodeId(), snapshot.hulls());
    }

    public static void clearCollisionGeometryCache() {
        collisionGeometryCache.clear();
    }

    public static void setRuntimeBodyOverride(long nodeId, float x, float y, float z, float yawDeg) {
        transformManager.setRuntimeBodyOverride(nodeId, x, y, z, yawDeg);
    }

    public static void clearRuntimeBodyOverride() {
        transformManager.clearRuntimeBodyOverride();
    }

    public static void clearMaterialTextureCache() {
        meshShader.clear();
        batchRenderer.clear();
        multiMeshRenderer.clear();
        decalRenderer.clear();
        pickingPass.clear();
        outlineRenderer.clear();
        com.moud.client.fabric.render.mesh.MoudMeshBuffer.cleanup();
    }

    public static void clearLights() {
        lightManager.clearLights();
    }

    public static float parseFloat(String value, float fallback) {
        return NodePropertyUtils.parseFloat(value, fallback);
    }

    public static boolean parseBool(String value, boolean fallback) {
        return NodePropertyUtils.parseBool(value, fallback);
    }

    public static float clamp01(float value) {
        return NodePropertyUtils.clamp01(value);
    }

    public static String stringProp(SceneSnapshot.NodeSnapshot node, String key) {
        return NodePropertyUtils.stringProp(node, key);
    }

    public static Pose worldPose(long nodeId) {
        return transformManager.worldPose(nodeId);
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
                com.moud.client.fabric.render.veil.VeilDynamicShaders.clear();
            } catch (Exception ignored) {
            }
        }

        float tickDelta = tickDelta(deltaTracker);
        cacheManager.refreshSceneCache(transformManager.runtimeOverrideVersion(), nodes -> {
            meshShader.onSnapshotUpdate(nodes);
            multiMeshRenderer.onSnapshotUpdate(nodes);
        });
        if (cacheManager.sceneChangedThisRefresh()) {
            if (cacheManager.sceneResetThisRefresh()) {
                transformManager.resetPoseStates();
            }
            transformManager.updatePoseStates(cacheManager.shiftPrevPoseState());
        }
        transformManager.beginPoseFrame(tickDelta);

        if (stage == VeilRenderLevelStageEvent.Stage.AFTER_SKY) {
            lightManager.syncLights(tickDelta);
        }
        if (stage == VeilRenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            if (bufferSource == null || camera == null) {
                return;
            }
            bufferSource.draw();
            renderMeshes(bufferSource, camera, frustumMatrix, projectionMatrix, frustum, tickDelta);
        }
    }

    private static void renderMeshes(VertexConsumerProvider.Immediate consumers,
                                     Camera camera,
                                     Matrix4fc frustumMatrix,
                                     Matrix4fc projectionMatrix,
                                     Frustum frustum,
                                     float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || cacheManager.isEmpty()) {
            return;
        }

        Vec3d camPos = camera.getPos();
        MatrixStack matrices = new MatrixStack();
        List<SceneSnapshot.NodeSnapshot> filteredNodes = cacheManager.filteredCachedNodes();

        try (ClientFrameProfiler.Scope ignored = ClientFrameProfiler.scope("render.scene")) {
            try (ClientFrameProfiler.Scope lights = ClientFrameProfiler.scope("render.scene.lights")) {
                meshShader.collectLights(filteredNodes, transformManager::worldPose);
            }

            try (ClientFrameProfiler.Scope batched = ClientFrameProfiler.scope("render.scene.batches")) {
                batchRenderer.renderBatched(filteredNodes, transformManager::worldPose, camPos, camera, frustumMatrix, projectionMatrix, client, tickDelta);
                multiMeshRenderer.renderAll(filteredNodes, transformManager::worldPose, camPos, camera, frustumMatrix, projectionMatrix, client, tickDelta);
            }

            try (ClientFrameProfiler.Scope manual = ClientFrameProfiler.scope("render.scene.manual")) {
                fallbackRenderer.render(filteredNodes, transformManager::worldPose, meshShader::renderNode, meshShader::resolveNodeTexture,
                        consumers, matrices, camPos, camera, frustumMatrix, projectionMatrix, client, tickDelta, textRenderer);
                consumers.draw();
            }

            try (ClientFrameProfiler.Scope particles = ClientFrameProfiler.scope("render.scene.particles")) {
                particleRenderer.render(filteredNodes, transformManager::worldPose, consumers, matrices, camPos, camera, frustum, client, tickDelta);
                consumers.draw();
            }

            try (ClientFrameProfiler.Scope decals = ClientFrameProfiler.scope("render.scene.decals")) {
                decalRenderer.renderAll(filteredNodes, transformManager::worldPose, camPos, camera, frustumMatrix, projectionMatrix, client, tickDelta);
            }

            // Viewmodel renders directly into MC's main framebuffer
            // alongside everything else. The post-process pass runs at
            // HudRenderCallback time (see MoudClient.renderOverlays),
            // after MC has composited world + entities + viewmodel +
            // translucent, so renderScale uniformly pixelates the whole
            // frame - including MC's player entity.
            try (ClientFrameProfiler.Scope viewmodels = ClientFrameProfiler.scope("render.scene.viewmodels")) {
                viewmodelRenderer.render(filteredNodes, consumers, matrices, camera);
            }
            renderEditor(camPos, frustumMatrix, projectionMatrix, client);

            if (collisionDebugEnabled) {
                try (ClientFrameProfiler.Scope debug = ClientFrameProfiler.scope("render.scene.collision_debug")) {
                    debugRenderer.render(filteredNodes, collisionGeometryCache, transformManager::worldPose);
                }
            }
        }
    }

    public static void runFullscreenPostProcess(MinecraftClient client) {
        if (PostProcessService.INSTANCE.effectCount() <= 0) {
            return;
        }
        try (ClientFrameProfiler.Scope post = ClientFrameProfiler.scope("overlay.postprocess")) {
            net.minecraft.client.gl.Framebuffer fb = client != null ? client.getFramebuffer() : null;
            if (fb == null) return;
            int width = fb.textureWidth;
            int height = fb.textureHeight;
            if (width <= 0 || height <= 0) return;
            // Bind MC's main framebuffer explicitly so sourceFbo detection
            // grabs the fully-composited frame (world + MC entities +
            // viewmodel) regardless of whatever was bound last.
            fb.beginWrite(false);
            PostProcessService.INSTANCE.renderAll(client, width, height);
        }
    }

    private static void renderPostProcess(MinecraftClient client) {
        if (PostProcessService.INSTANCE.effectCount() <= 0) {
            return;
        }
        try (ClientFrameProfiler.Scope post = ClientFrameProfiler.scope("render.scene.postprocess")) {
            int[] viewport = new int[4];
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, viewport);
            int width = viewport[2];
            int height = viewport[3];
            if (width <= 0 || height <= 0) {
                net.minecraft.client.gl.Framebuffer framebuffer = client.getFramebuffer();
                if (framebuffer != null) {
                    width = framebuffer.textureWidth;
                    height = framebuffer.textureHeight;
                }
            }
            if (width > 0 && height > 0) {
                PostProcessService.INSTANCE.renderAll(client, width, height);
            }
        }
    }

    private static void renderEditor(Vec3d camPos,
                                     Matrix4fc frustumMatrix,
                                     Matrix4fc projectionMatrix,
                                     MinecraftClient client) {
        EditorContext editorContext = EditorOverlayBus.get();
        if (editorContext == null || !editorContext.isActive()) {
            return;
        }
        try (ClientFrameProfiler.Scope editor = ClientFrameProfiler.scope("render.scene.editor")) {
            List<SceneSnapshot.NodeSnapshot> filteredNodes = cacheManager.filteredCachedNodes();
            pickingPass.render(
                    filteredNodes,
                    transformManager::worldPose,
                    camPos,
                    frustumMatrix,
                    projectionMatrix,
                    editorContext.viewportW(),
                    editorContext.viewportH(),
                    editorContext.mouseViewportNdcX(),
                    editorContext.mouseViewportNdcY()
            );
            editorContext.setHoveredNodeId(pickingPass.hoveredNodeId());
            outlineRenderer.render(
                    filteredNodes,
                    cacheManager.cachedNodesById(),
                    transformManager::worldPose,
                    camPos,
                    frustumMatrix,
                    projectionMatrix,
                    editorContext.hoveredNodeId(),
                    editorContext.selectedNodeId(),
                    client
            );
        }
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
}
