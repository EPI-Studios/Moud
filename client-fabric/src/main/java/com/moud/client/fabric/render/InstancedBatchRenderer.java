package com.moud.client.fabric.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.fabric.render.mesh.MoudMeshBuffer;
import com.moud.client.fabric.render.MoudTextures;
import com.moud.client.fabric.render.scene.math.Pose;
import com.moud.client.fabric.render.shadow.ShadowMaps;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import com.moud.client.fabric.render.veil.GlUtil;
import com.moud.client.fabric.render.veil.VeilDynamicShaders;
import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.net.protocol.SceneSnapshot;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.block.ShaderBlock;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.registry.VeilShaderBufferRegistry;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

final class InstancedBatchRenderer {


    private static final int FLOATS_PER_INSTANCE = 20;
    private static final int INITIAL_CAPACITY    = 64;

    private final String instancedVert;
    private final String instancedFrag;
    private ShaderProgram instancedProgram;
    private int instanceVbo;
    private int instanceVboCapacity;
    private FloatBuffer instanceBuffer;
    private final Map<Long, Integer> vaoCache = new ConcurrentHashMap<>();
    private final SceneLights sceneLights;
    private final Matrix4f scratchWorldMat = new Matrix4f();
    private long cachedSceneVersion = Long.MIN_VALUE;
    private boolean cachedPlayRuntimeActive;
    private Map<String, List<BatchInstance>> cachedBatches = Map.of();

    InstancedBatchRenderer(SceneLights sceneLights) {
        this.sceneLights = sceneLights;
        instancedVert = loadResource("assets/moud/shaders/builtin/default_mesh_instanced.vert");
        instancedFrag = loadResource("assets/moud/shaders/builtin/default_mesh_instanced.frag");
    }

    int renderBatched(List<SceneSnapshot.NodeSnapshot> nodes,
                      Function<Long, Pose> poseResolver,
                      Vec3d camPos, Camera camera, Matrix4fc viewMatrix, Matrix4fc projectionMatrix,
                      MinecraftClient client, float tickDelta) {
        if (!RenderSystem.isOnRenderThread()) return 0;

        ShaderProgram program = getOrCompileProgram();
        if (program == null || !program.isValid()) return 0;

        MoudMeshBuffer.ensureInitialized();

        Map<String, List<BatchInstance>> batches = batchesFor(nodes);
        if (batches.isEmpty()) return 0;

        Matrix4f viewMat = viewMatrix != null ? new Matrix4f(viewMatrix) : new Matrix4f();
        Matrix4f projMat = projectionMatrix != null ? new Matrix4f(projectionMatrix) : new Matrix4f(RenderSystem.getProjectionMatrix());

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        int rendered = 0;

        try {
            VeilRenderSystem.setShader(program);
            program.bind();

            int pid = GlUtil.currentProgram();
            uploadFrameUniforms(pid, viewMat, projMat, camPos, client, tickDelta);

            program.clearSamplers();
            program.setSampler("Texture0", MoudTextures.boundGlId(MoudTextures.white()), 0);
            Identifier ibrShadowId = ShadowMaps.hasActiveSpotShadow()
                    ? ShadowMaps.spotShadowTextureId() : MoudTextures.white();
            program.setSampler("SpotShadowMap", MoudTextures.boundGlId(ibrShadowId), 0);
            program.bindSamplers(0);

            for (var entry : batches.entrySet()) {
                List<BatchInstance> instances = entry.getValue();
                if (instances.isEmpty()) continue;

                String batchKey = entry.getKey();
                boolean doubleSided = batchKey.endsWith(":ds");
                String meshKey = doubleSided ? batchKey.substring(0, batchKey.length() - 3) : batchKey;

                var mesh = resolveMesh(meshKey);
                ensureInstanceVbo(instances.size());
                int uploadedInstances = fillInstanceData(instances, poseResolver);
                if (uploadedInstances <= 0) continue;
                uploadInstanceData();

                long key = ((long) pid << 32) | (mesh.vbo & 0xFFFFFFFFL);
                int vao = vaoCache.computeIfAbsent(key,
                        k -> GlUtil.createInstancedMeshVao(pid, mesh.vbo, mesh.ebo, instanceVbo));

                if (doubleSided) RenderSystem.disableCull();
                GlUtil.drawElementsInstanced(vao, mesh.indexCount, uploadedInstances);
                if (doubleSided) RenderSystem.enableCull();
                rendered += uploadedInstances;
            }
        } finally {
            ShaderProgram.unbind();
        }

        return rendered;
    }

    private Map<String, List<BatchInstance>> batchesFor(List<SceneSnapshot.NodeSnapshot> nodes) {
        long sceneVersion = ClientSceneBus.version();
        boolean playRuntimeActive = isPlayRuntimeActive();
        if (cachedSceneVersion == sceneVersion && cachedPlayRuntimeActive == playRuntimeActive) {
            return cachedBatches;
        }
        cachedSceneVersion = sceneVersion;
        cachedPlayRuntimeActive = playRuntimeActive;
        cachedBatches = buildBatches(nodes, playRuntimeActive);
        return cachedBatches;
    }

    private Map<String, List<BatchInstance>> buildBatches(List<SceneSnapshot.NodeSnapshot> nodes,
                                                          boolean playRuntimeActive) {
        Map<String, List<BatchInstance>> batches = new HashMap<>();
        for (var node : nodes) {
            if (node == null) continue;
            String type = node.type();
            if (!"MeshInstance3D".equals(type) && !"CSGBox".equals(type)) continue;
            if (!VeilSceneNodeRenderer.parseBool(VeilSceneNodeRenderer.stringProp(node, "visible"), true)) continue;
            if (VeilSceneNodeRenderer.parseBool(VeilSceneNodeRenderer.stringProp(node, "viewmodel"), false)) {
                if (playRuntimeActive) continue;
            }

            String materialPath = VeilSceneNodeRenderer.stringProp(node, "material");
            if (materialPath != null && !materialPath.isBlank()) continue;

            String meshSource = VeilSceneNodeRenderer.stringProp(node, "mesh_source");
            if (meshSource != null && !meshSource.isBlank()) continue;

            String texProp = VeilSceneNodeRenderer.stringProp(node, "texture");
            boolean hasCustomTexture = texProp != null && !texProp.isBlank()
                    && !MoudTextures.WHITE_ID.toString().equals(texProp)
                    && !"moud:dynamic/white".equals(texProp);
            if (hasCustomTexture) continue;

            float tintR   = clampedProp(node, "color_tint_r", 1f);
            float tintG   = clampedProp(node, "color_tint_g", 1f);
            float tintB   = clampedProp(node, "color_tint_b", 1f);
            float opacity = clampedProp(node, "opacity", 1f);

            String mesh = VeilSceneNodeRenderer.stringProp(node, "mesh");
            if (mesh == null || mesh.isBlank()) mesh = "cube";

            boolean doubleSided = VeilSceneNodeRenderer.parseBool(
                    VeilSceneNodeRenderer.stringProp(node, "double_sided"), false);
            String batchKey = doubleSided ? mesh + ":ds" : mesh;

            batches.computeIfAbsent(batchKey, k -> new ArrayList<>())
                    .add(new BatchInstance(node.nodeId(), tintR, tintG, tintB, opacity));
        }
        return batches;
    }

    private static boolean isPlayRuntimeActive() {
        PlayRuntimeClient rt = PlayRuntimeBus.get();
        return rt != null && rt.isActive();
    }

    private void uploadFrameUniforms(int pid, Matrix4f view, Matrix4f proj, Vec3d camPos,
                                     MinecraftClient client, float tickDelta) {
        GlUtil.uniformMat4(pid, "ViewMat", view);
        GlUtil.uniformMat4(pid, "ProjMat", proj);
        GlUtil.uniform3f(pid, "CameraPos", (float) camPos.x, (float) camPos.y, (float) camPos.z);

        if (client.world != null) {
            long totalTicks = client.world.getTime();
            int dayTicks = (int) (client.world.getTimeOfDay() % 24_000L);
            GlUtil.uniform1i(pid, "TimeTicks", dayTicks);
            GlUtil.uniform1f(pid, "GameTime", (totalTicks + tickDelta) / 24_000f);
            GlUtil.uniform1f(pid, "Time", (totalTicks + tickDelta) * 0.05f);
        }
        GlUtil.uniform1f(pid, "DeltaTime", tickDelta);
        sceneLights.applyUniforms(pid);
        ShadowMaps.uploadSpotShadowScalarUniforms(pid);
    }

    private record MeshHandles(int vbo, int ebo, int indexCount) {}

    private static MeshHandles resolveMesh(String meshType) {
        return switch (meshType) {
            case "subdivided_plane" -> {
                MoudMeshBuffer.ensurePlaneInitialized();
                yield new MeshHandles(MoudMeshBuffer.planeVbo(), MoudMeshBuffer.planeEbo(), MoudMeshBuffer.planeIndexCount());
            }
            case "plane", "quad", "sprite_quad" -> {
                MoudMeshBuffer.ensureQuadInitialized();
                yield new MeshHandles(MoudMeshBuffer.quadVbo(), MoudMeshBuffer.quadEbo(), MoudMeshBuffer.quadIndexCount());
            }
            case "sphere" -> {
                MoudMeshBuffer.ensureSphereInitialized();
                yield new MeshHandles(MoudMeshBuffer.sphereVbo(), MoudMeshBuffer.sphereEbo(), MoudMeshBuffer.sphereIndexCount());
            }
            default -> new MeshHandles(MoudMeshBuffer.vbo(), MoudMeshBuffer.ebo(), MoudMeshBuffer.indexCount());
        };
    }

    private void ensureInstanceVbo(int count) {
        if (instanceVbo == 0) instanceVbo = GL15.glGenBuffers();
        int needed = count * FLOATS_PER_INSTANCE;
        if (instanceBuffer == null || instanceBuffer.capacity() < needed) {
            if (instanceBuffer != null) MemoryUtil.memFree(instanceBuffer);
            int cap = Math.max(INITIAL_CAPACITY * FLOATS_PER_INSTANCE, needed);
            instanceBuffer = MemoryUtil.memAllocFloat(cap);
            instanceVboCapacity = cap;
        }
    }

    private int fillInstanceData(List<BatchInstance> instances, Function<Long, Pose> poseResolver) {
        instanceBuffer.clear();
        int written = 0;
        for (var inst : instances) {
            Pose world = poseResolver.apply(inst.nodeId);
            if (world == null) continue;
            scratchWorldMat.identity()
                    .translate(world.pos.x, world.pos.y, world.pos.z)
                    .rotate(world.rot)
                    .scale(world.scale.x, world.scale.y, world.scale.z)
                    .translate(-0.5f, -0.5f, -0.5f)
                    .get(instanceBuffer);
            instanceBuffer.position(instanceBuffer.position() + 16);
            instanceBuffer.put(inst.tintR).put(inst.tintG).put(inst.tintB).put(inst.opacity);
            written++;
        }
        instanceBuffer.flip();
        return written;
    }

    private void uploadInstanceData() {
        int prevArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, instanceBuffer, GL15.GL_STREAM_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevArrayBuffer);
    }

    private ShaderProgram getOrCompileProgram() {
        if (instancedProgram != null && instancedProgram.isValid()) return instancedProgram;
        if (instancedVert.isEmpty() || instancedFrag.isEmpty()) return null;
        Identifier id = Identifier.of("moud", "builtin/default_mesh_instanced");
        Int2ObjectMap<String> stages = new Int2ObjectArrayMap<>();
        stages.put(GL20C.GL_VERTEX_SHADER, instancedVert);
        stages.put(GL20C.GL_FRAGMENT_SHADER, instancedFrag);
        instancedProgram = VeilDynamicShaders.getOrCompile(id, stages);
        return instancedProgram;
    }

    void clear() {
        vaoCache.values().forEach(GlUtil::deleteVao);
        vaoCache.clear();
        if (instanceVbo != 0) { GL15.glDeleteBuffers(instanceVbo); instanceVbo = 0; }
        if (instanceBuffer != null) { MemoryUtil.memFree(instanceBuffer); instanceBuffer = null; }
        instancedProgram = null;
        cachedSceneVersion = Long.MIN_VALUE;
        cachedPlayRuntimeActive = false;
        cachedBatches = Map.of();
    }

    private static float clampedProp(SceneSnapshot.NodeSnapshot node, String key, float def) {
        return VeilSceneNodeRenderer.clamp01(
                VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, key), def));
    }

    private static String loadResource(String path) {
        try (InputStream is = InstancedBatchRenderer.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        return "";
    }

    private record BatchInstance(long nodeId, float tintR, float tintG, float tintB, float opacity) {}
}
