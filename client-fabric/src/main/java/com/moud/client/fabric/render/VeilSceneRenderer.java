package com.moud.client.fabric.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.fabric.editor.diagnostics.ClientFrameProfiler;
import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;
import com.moud.client.fabric.player.PlayerBodyAttachmentCache;
import com.moud.client.fabric.render.shadow.ShadowMaps;
import com.moud.client.fabric.render.shadow.ShadowPass;
import com.moud.client.fabric.render.picking.NodePickingPass;
import com.moud.client.fabric.render.picking.OutlineRenderer;
import com.moud.client.fabric.render.scene.light.SceneLightManager;
import com.moud.client.fabric.render.scene.math.Pose;
import com.moud.client.fabric.render.scene.state.SceneCacheManager;
import com.moud.client.fabric.render.scene.state.TransformManager;
import com.moud.client.fabric.render.mesh.MoudMeshBuffer;
import com.moud.client.fabric.render.mesh.ProceduralMeshGpuCache;
import com.moud.client.fabric.render.scene.subrender.FallbackMeshRenderer;
import com.moud.client.fabric.render.veil.VeilDynamicShaders;
import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.client.fabric.scene.MoudTickClock;
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
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

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

    static SceneLights sharedLights() { return meshShader.sceneLights(); }

    private static final Matrix4f lastViewMatrix = new Matrix4f();
    private static final Matrix4f lastProjectionMatrix = new Matrix4f();
    private static final Vector3f lastCameraPos = new Vector3f();
    private static volatile boolean lastMatricesValid = false;

    static Matrix4f lastViewMatrix() { return lastMatricesValid ? lastViewMatrix : null; }
    static Matrix4f lastProjectionMatrix() { return lastMatricesValid ? lastProjectionMatrix : null; }
    static Vector3f lastCameraPos() { return lastMatricesValid ? lastCameraPos : null; }
    private static final InstancedBatchRenderer batchRenderer = new InstancedBatchRenderer(meshShader.sceneLights());
    private static final MultiMeshRenderer multiMeshRenderer = new MultiMeshRenderer(meshShader.sceneLights());
    private static final DecalRenderer decalRenderer = new DecalRenderer(meshShader);
    private static final PostProcessNodeSync postProcessNodeSync = new PostProcessNodeSync();
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

    public static void resetPoseStates() {
        transformManager.resetPoseStates();
    }

    public static void clearMaterialTextureCache() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(VeilSceneRenderer::clearMaterialTextureCache);
            return;
        }
        meshShader.clear();
        batchRenderer.clear();
        multiMeshRenderer.clear();
        decalRenderer.clear();
        pickingPass.clear();
        outlineRenderer.clear();
        ProceduralMeshGpuCache.clear();
        MoudMeshBuffer.cleanup();
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
                VeilDynamicShaders.clear();
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
            Vec3d capturePos = camera.getPos();
            lastViewMatrix.set(frustumMatrix);
            lastProjectionMatrix.set(projectionMatrix);
            lastCameraPos.set((float) capturePos.x, (float) capturePos.y, (float) capturePos.z);
            lastMatricesValid = true;

            bufferSource.draw();
            renderMeshes(bufferSource, camera, frustumMatrix, projectionMatrix, frustum, tickDelta);
        }
    }

    private static void collectAndRenderSpotShadows(List<SceneSnapshot.NodeSnapshot> nodes) {
        ShadowMaps.resetFrame();
        SceneLights lights = meshShader.sceneLights();
        if (lights == null) return;
        List<SceneLights.SpotLight> spots = lights.spotLights();
        Vector3f camPos = lastCameraPos();
        float cx = camPos != null ? camPos.x : 0f;
        float cy = camPos != null ? camPos.y : 0f;
        float cz = camPos != null ? camPos.z : 0f;

        java.util.ArrayList<int[]> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < spots.size(); i++) {
            SceneLights.SpotLight s = spots.get(i);
            if (!s.castShadows()) continue;
            float dx = s.x() - cx;
            float dy = s.y() - cy;
            float dz = s.z() - cz;
            int distSq = Float.floatToRawIntBits(dx * dx + dy * dy + dz * dz);
            candidates.add(new int[] { i, distSq });
        }
        if (candidates.isEmpty()) return;
        candidates.sort((a, b) -> Float.compare(
                Float.intBitsToFloat(a[1]),
                Float.intBitsToFloat(b[1])));

        java.util.ArrayList<ShadowPass.SpotCaster> casters = new java.util.ArrayList<>();
        for (int[] entry : candidates) {
            int lightIdx = entry[0];
            SceneLights.SpotLight s = spots.get(lightIdx);
            ShadowPass.SpotCaster probe = new ShadowPass.SpotCaster(
                    lightIdx, s.x(), s.y(), s.z(), s.dx(), s.dy(), s.dz(),
                    s.angleDeg(), s.distance(), -1);
            Matrix4f vp = ShadowPass.buildSpotViewProj(probe);
            int slot = ShadowMaps.addSpotCaster(lightIdx, vp);
            if (slot < 0) break;
            casters.add(new ShadowPass.SpotCaster(
                    lightIdx, s.x(), s.y(), s.z(), s.dx(), s.dy(), s.dz(),
                    s.angleDeg(), s.distance(), slot));
        }
        if (casters.isEmpty()) return;

        long sceneRev = cacheManager.cachedNodes().isEmpty() ? 0L : ClientSceneBus.version();
        if (ShadowMaps.cachedStaticRevision() != sceneRev) {
            ShadowMaps.invalidateAllCaches();
            ShadowMaps.updateCachedStaticRevision(sceneRev);
        }

        ShadowPass.renderSpotShadows(casters, nodes, transformManager::worldPose,
                (node, world, vp) -> meshShader.renderNodeDepthOnly(node, world, vp));
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

        lastViewMatrix.set(frustumMatrix);
        lastProjectionMatrix.set(projectionMatrix);
        lastCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        lastMatricesValid = true;

        try (ClientFrameProfiler.Scope ignored = ClientFrameProfiler.scope("render.scene")) {
            PlayerBodyAttachmentCache.primeFromSnapshot(filteredNodes);
            postProcessNodeSync.sync(filteredNodes);
            try (ClientFrameProfiler.Scope lights = ClientFrameProfiler.scope("render.scene.lights")) {
                meshShader.collectLights(filteredNodes, transformManager::worldPose);
            }

            try (ClientFrameProfiler.Scope shadows = ClientFrameProfiler.scope("render.scene.shadows")) {
                collectAndRenderSpotShadows(filteredNodes);
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
        runFullscreenPostProcess(client, PostProcessStage.WORLD);
    }

    public static void runFullscreenPostProcess(MinecraftClient client, PostProcessStage stage) {
        if (PostProcessService.INSTANCE.effectCount() <= 0) {
            return;
        }
        if (EditorOverlayBus.isActive()) {
            return;
        }
        String scopeName = stage == PostProcessStage.SCREEN
                ? "overlay.postprocess.screen"
                : "overlay.postprocess.world";
        try (ClientFrameProfiler.Scope post = ClientFrameProfiler.scope(scopeName)) {
            Framebuffer fb = client != null ? client.getFramebuffer() : null;
            if (fb == null) return;
            int width = fb.textureWidth;
            int height = fb.textureHeight;
            if (width <= 0 || height <= 0) return;
            fb.beginWrite(false);
            PostProcessService.INSTANCE.renderAll(client, width, height, stage);
        }
    }

    private static void renderPostProcess(MinecraftClient client) {
        if (PostProcessService.INSTANCE.effectCount() <= 0) {
            return;
        }
        if (EditorOverlayBus.isActive()) {
            return;
        }
        try (ClientFrameProfiler.Scope post = ClientFrameProfiler.scope("render.scene.postprocess")) {
            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            int width = viewport[2];
            int height = viewport[3];
            if (width <= 0 || height <= 0) {
                Framebuffer framebuffer = client.getFramebuffer();
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
