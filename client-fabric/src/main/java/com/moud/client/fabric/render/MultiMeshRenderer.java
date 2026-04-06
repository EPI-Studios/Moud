package com.moud.client.fabric.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.fabric.render.mesh.MoudMeshBuffer;
import com.moud.client.fabric.render.veil.GlUtil;
import com.moud.client.fabric.render.veil.VeilDynamicShaders;
import com.moud.client.fabric.render.veil.VeilMaterialBinding;
import com.moud.net.protocol.SceneSnapshot;
import foundry.veil.api.client.render.CameraMatrices;
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
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MultiMeshRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiMeshRenderer.class);

    // WorldMat(16) + Tint(4), camera offset applied in shader
    private static final int FLOATS_PER_GPU_INSTANCE = 20;
    private static final int FLOATS_PER_INSTANCE = 13;
    private static final int INITIAL_CAPACITY = 64;

    private final String defaultVert;
    private final String defaultFrag;
    private ShaderProgram defaultProgram;

    private final Map<Long, VeilMaterialBinding> materialBindings = new ConcurrentHashMap<>();
    private final Map<Long, NodeGpuState> nodeStates = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, float[]>> prevUniforms = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, float[]>> currUniforms = new ConcurrentHashMap<>();
    private final SceneLights sceneLights;
    private final Set<String> loggedShaderErrors = new HashSet<>();

    private static final class NodeGpuState {
        int vbo = 0;
        int vao = 0;
        int vaoProgram = 0;
        int instanceCount = 0;
        float[] dataRef = null;
        float px, py, pz, qx, qy, qz, qw;

        boolean poseMatches(VeilSceneNodeRenderer.Pose p) {
            return px == (float) p.pos.x && py == (float) p.pos.y && pz == (float) p.pos.z
                    && qx == p.rot.x && qy == p.rot.y && qz == p.rot.z && qw == p.rot.w;
        }

        void capturePose(VeilSceneNodeRenderer.Pose p) {
            px = (float) p.pos.x; py = (float) p.pos.y; pz = (float) p.pos.z;
            qx = p.rot.x; qy = p.rot.y; qz = p.rot.z; qw = p.rot.w;
        }
    }

    MultiMeshRenderer(SceneLights sceneLights) {
        this.sceneLights = sceneLights;
        defaultVert = loadResource("assets/moud/shaders/builtin/default_mesh_instanced.vert");
        defaultFrag = loadResource("assets/moud/shaders/builtin/default_mesh_instanced.frag");
    }

    void renderAll(List<SceneSnapshot.NodeSnapshot> nodes,
                   Function<Long, VeilSceneNodeRenderer.Pose> poseResolver,
                   Vec3d camPos, Camera camera, Matrix4fc viewMatrix, Matrix4fc projectionMatrix,
                   MinecraftClient client, float tickDelta) {
        if (!RenderSystem.isOnRenderThread()) return;

        MoudMeshBuffer.ensureInitialized();

        ShaderBlock<CameraMatrices> camBlock = VeilRenderSystem.getBlock(VeilShaderBufferRegistry.CAMERA.get());
        CameraMatrices veilCam = camBlock != null ? camBlock.getValue() : null;
        Matrix4f viewMat = veilCam != null ? new Matrix4f(veilCam.getViewMatrix())
                : (viewMatrix != null ? new Matrix4f(viewMatrix) : new Matrix4f());
        Matrix4f projMat = veilCam != null ? new Matrix4f(veilCam.getProjectionMatrix())
                : (projectionMatrix != null ? new Matrix4f(projectionMatrix) : new Matrix4f(RenderSystem.getProjectionMatrix()));

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        ShaderProgram currentProgram = null;
        int currentPid = 0;

        try {
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null || !"MultiMeshInstance3D".equals(node.type())) continue;
            if (!VeilSceneNodeRenderer.parseBool(VeilSceneNodeRenderer.stringProp(node, "visible"), true)) continue;

            float[] data = InstanceDataStore.get(node.nodeId());
            if (data == null || data.length < FLOATS_PER_INSTANCE) continue;
            int instanceCount = data.length / FLOATS_PER_INSTANCE;
            if (instanceCount == 0) continue;

            String materialPath = VeilSceneNodeRenderer.stringProp(node, "material");
            VeilMaterialBinding binding = null;
            ShaderProgram program;

            if (materialPath != null && !materialPath.isBlank()) {
                binding = materialBindings.computeIfAbsent(node.nodeId(), id -> new VeilMaterialBinding());
                binding.configure(materialPath, null);
                program = resolveInstancingProgram(binding);
            } else {
                program = getOrCompileDefaultProgram();
            }

            if (program == null || !program.isValid()) {
                continue;
            }

            if (program != currentProgram) {
                if (currentProgram != null) ShaderProgram.unbind();
                VeilRenderSystem.setShader(program);
                program.bind();
                currentProgram = program;
                currentPid = GlUtil.currentProgram();

                GlUtil.uniformMat4(currentPid, "ViewMat", viewMat);
                GlUtil.uniformMat4(currentPid, "ProjMat", projMat);
                GlUtil.uniform3f(currentPid, "CameraPos", (float) camPos.x, (float) camPos.y, (float) camPos.z);
                if (client.world != null) {
                    long totalTicks = client.world.getTime();
                    GlUtil.uniform1i(currentPid, "TimeTicks", (int) (client.world.getTimeOfDay() % 24_000L));
                    GlUtil.uniform1f(currentPid, "GameTime", (totalTicks + tickDelta) / 24_000f);
                    GlUtil.uniform1f(currentPid, "Time", (totalTicks + tickDelta) * 0.05f);
                }
                GlUtil.uniform1f(currentPid, "DeltaTime", tickDelta);
                sceneLights.applyUniforms(currentPid);
            }

            if (binding != null) {
                binding.applyMaterial(program);
            } else {
                program.clearSamplers();
                program.setSampler("Texture0", MoudTextures.white());
            }
            program.bindSamplers(0);

            List<SceneSnapshot.Uniform> uniforms = node.uniforms();
            if (uniforms != null && !uniforms.isEmpty()) {
                applyInterpolatedUniforms(currentPid, node.nodeId(), uniforms, tickDelta);
            }

            String meshType = VeilSceneNodeRenderer.stringProp(node, "mesh");
            var mesh = resolveMesh(meshType);

            boolean isCross = "cross".equals(meshType);
            if (isCross) GL11.glDisable(GL11.GL_CULL_FACE);
            RenderSystem.depthMask(true);

            VeilSceneNodeRenderer.Pose nodePose = poseResolver.apply(node.nodeId());
            if (nodePose == null) nodePose = VeilSceneNodeRenderer.Pose.IDENTITY;

            NodeGpuState state = nodeStates.computeIfAbsent(node.nodeId(), k -> new NodeGpuState());

            if (data != state.dataRef || !state.poseMatches(nodePose)) {
                ensureNodeVbo(state, instanceCount);
                fillAndUpload(state, data, instanceCount, isCross, nodePose);
                state.dataRef = data;
                state.instanceCount = instanceCount;
                state.capturePose(nodePose);
            }

            if (state.vao == 0 || state.vaoProgram != currentPid) {
                if (state.vao != 0) GlUtil.deleteVao(state.vao);
                state.vao = GlUtil.createInstancedMeshVao(currentPid, mesh.vbo, mesh.ebo, state.vbo);
                state.vaoProgram = currentPid;
            }

            GlUtil.drawElementsInstanced(state.vao, mesh.indexCount, state.instanceCount);

            if (isCross) GL11.glEnable(GL11.GL_CULL_FACE);
        }

        } finally {
            if (currentProgram != null) ShaderProgram.unbind();
            RenderSystem.disableBlend();
        }
    }

    // if the material only has a fragment shader, splice in the default instanced vertex shader
    private ShaderProgram resolveInstancingProgram(VeilMaterialBinding binding) {
        binding.resolveProgram();
        var sf = binding.shaderFile();
        Identifier baseId = binding.programId();
        if (sf == null || baseId == null) return null;

        Int2ObjectMap<String> stages = sf.stageSources();
        String vertSrc = stages.get(GL20C.GL_VERTEX_SHADER);
        boolean isBlit = vertSrc == null || vertSrc.contains("gl_VertexID");

        Identifier compileId;
        Int2ObjectMap<String> compileStages;
        if (isBlit) {
            compileId = Identifier.of(baseId.getNamespace(), "inst/" + baseId.getPath());
            compileStages = new Int2ObjectArrayMap<>(stages);
            compileStages.put(GL20C.GL_VERTEX_SHADER, defaultVert);
        } else {
            compileId = baseId;
            compileStages = stages;
        }

        ShaderProgram program = VeilDynamicShaders.getOrCompile(compileId, compileStages);
        if (program == null) {
            String err = VeilDynamicShaders.getLastError(compileId);
            if (err != null && loggedShaderErrors.add(compileId + ":" + err)) {
                LOGGER.error("[Moud] MultiMesh shader compilation failed material={} shader={} program={} error={}",
                        binding.materialPath(),
                        binding.shaderPath(),
                        compileId,
                        err);
            }
        }
        return program;
    }

    private ShaderProgram getOrCompileDefaultProgram() {
        if (defaultProgram != null && defaultProgram.isValid()) return defaultProgram;
        if (defaultVert.isEmpty() || defaultFrag.isEmpty()) return null;
        Identifier id = Identifier.of("moud", "builtin/default_mesh_instanced");
        Int2ObjectMap<String> stages = new Int2ObjectArrayMap<>();
        stages.put(GL20C.GL_VERTEX_SHADER, defaultVert);
        stages.put(GL20C.GL_FRAGMENT_SHADER, defaultFrag);
        defaultProgram = VeilDynamicShaders.getOrCompile(id, stages);
        if (defaultProgram == null) {
            String err = VeilDynamicShaders.getLastError(id);
            if (err != null && loggedShaderErrors.add(id + ":" + err)) {
                LOGGER.error("[Moud] MultiMesh default shader compilation failed program={} error={}", id, err);
            }
        }
        return defaultProgram;
    }

    private void ensureNodeVbo(NodeGpuState state, int instanceCount) {
        if (state.vbo == 0) state.vbo = GL15.glGenBuffers();
    }

    private static void fillAndUpload(NodeGpuState state, float[] data, int instanceCount,
                                      boolean isCross, VeilSceneNodeRenderer.Pose nodePose) {
        int needed = instanceCount * FLOATS_PER_GPU_INSTANCE;
        FloatBuffer buf = MemoryUtil.memAllocFloat(needed);
        try {
            Quaternionf quat = new Quaternionf();
            Matrix4f localMat = new Matrix4f();
            Matrix4f worldMat = new Matrix4f();
            // no scale on the node because sx/sz are instance field dimensions (remember that)
            Matrix4f nodeWorldBase = new Matrix4f()
                    .translate(nodePose.pos.x, nodePose.pos.y, nodePose.pos.z)
                    .rotate(nodePose.rot);

            float pivotX = isCross ? 0f : -0.5f;
            float pivotY = isCross ? 0f : -0.5f;
            float pivotZ = isCross ? 0f : -0.5f;

            for (int i = 0; i < instanceCount; i++) {
                int b = i * FLOATS_PER_INSTANCE;
                float px = data[b],    py = data[b+1], pz = data[b+2];
                float qx = data[b+3],  qy = data[b+4], qz = data[b+5], qw = data[b+6];
                float sx = data[b+7],  sy = data[b+8], sz = data[b+9];
                float cr = data[b+10], cg = data[b+11], cb = data[b+12];

                quat.set(qx, qy, qz, qw);
                localMat.identity()
                        .translate(px, py, pz)
                        .rotate(quat)
                        .scale(sx, sy, sz)
                        .translate(pivotX, pivotY, pivotZ);

                nodeWorldBase.mul(localMat, worldMat);
                worldMat.get(buf);
                buf.position(buf.position() + 16);
                buf.put(cr).put(cg).put(cb).put(1.0f);
            }
            buf.flip();

            int prev = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, state.vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prev);
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    private record MeshHandles(int vbo, int ebo, int indexCount) {}

    private static MeshHandles resolveMesh(String meshType) {
        return switch (meshType == null ? "box" : meshType) {
            case "plane" -> {
                MoudMeshBuffer.ensurePlaneInitialized();
                yield new MeshHandles(MoudMeshBuffer.planeVbo(), MoudMeshBuffer.planeEbo(), MoudMeshBuffer.planeIndexCount());
            }
            case "sphere" -> {
                MoudMeshBuffer.ensureSphereInitialized();
                yield new MeshHandles(MoudMeshBuffer.sphereVbo(), MoudMeshBuffer.sphereEbo(), MoudMeshBuffer.sphereIndexCount());
            }
            case "cross" -> {
                MoudMeshBuffer.ensureCrossInitialized();
                yield new MeshHandles(MoudMeshBuffer.crossVbo(), MoudMeshBuffer.crossEbo(), MoudMeshBuffer.crossIndexCount());
            }
            default -> new MeshHandles(MoudMeshBuffer.vbo(), MoudMeshBuffer.ebo(), MoudMeshBuffer.indexCount());
        };
    }

    void clear() {
        for (NodeGpuState state : nodeStates.values()) {
            if (state.vao != 0) GlUtil.deleteVao(state.vao);
            if (state.vbo != 0) GL15.glDeleteBuffers(state.vbo);
        }
        nodeStates.clear();
        materialBindings.clear();
        prevUniforms.clear();
        currUniforms.clear();
        defaultProgram = null;
    }

    void onSnapshotUpdate(List<SceneSnapshot.NodeSnapshot> nodes) {
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null) continue;
            List<SceneSnapshot.Uniform> uniforms = node.uniforms();
            if (uniforms == null || uniforms.isEmpty()) continue;
            long id = node.nodeId();

            Map<String, float[]> curr = currUniforms.get(id);
            if (curr != null && !curr.isEmpty()) {
                Map<String, float[]> prev = prevUniforms.computeIfAbsent(id, k -> new HashMap<>());
                for (Map.Entry<String, float[]> e : curr.entrySet()) {
                    prev.put(e.getKey(), e.getValue().clone());
                }
            }

            Map<String, float[]> next = currUniforms.computeIfAbsent(id, k -> new HashMap<>());
            for (SceneSnapshot.Uniform u : uniforms) {
                if (u == null || u.key() == null || u.values() == null) continue;
                List<Float> vals = u.values();
                float[] arr = new float[vals.size()];
                for (int i = 0; i < vals.size(); i++) arr[i] = vals.get(i);
                next.put(u.key(), arr);
            }
        }
    }

    private void applyInterpolatedUniforms(int pid, long nodeId, List<SceneSnapshot.Uniform> uniforms, float tickDelta) {
        Map<String, float[]> prev = prevUniforms.get(nodeId);
        Map<String, float[]> curr = currUniforms.get(nodeId);

        for (SceneSnapshot.Uniform u : uniforms) {
            if (u == null || u.key() == null || u.values() == null) continue;
            String key = u.key();
            List<Float> vals = u.values();
            int size = vals.size();
            if (size < 1 || size > 4) continue;

            float[] c = curr != null ? curr.get(key) : null;
            float[] p = prev != null ? prev.get(key) : null;

            if (c != null && p != null && c.length == p.length) {
                float t = tickDelta;
                switch (size) {
                    case 1 -> GlUtil.uniform1f(pid, key, p[0] + (c[0] - p[0]) * t);
                    case 2 -> GlUtil.uniform2f(pid, key,
                            p[0] + (c[0] - p[0]) * t,
                            p[1] + (c[1] - p[1]) * t);
                    case 3 -> GlUtil.uniform3f(pid, key,
                            p[0] + (c[0] - p[0]) * t,
                            p[1] + (c[1] - p[1]) * t,
                            p[2] + (c[2] - p[2]) * t);
                    case 4 -> GlUtil.uniform4f(pid, key,
                            p[0] + (c[0] - p[0]) * t,
                            p[1] + (c[1] - p[1]) * t,
                            p[2] + (c[2] - p[2]) * t,
                            p[3] + (c[3] - p[3]) * t);
                }
            } else {
                switch (size) {
                    case 1 -> GlUtil.uniform1f(pid, key, vals.get(0));
                    case 2 -> GlUtil.uniform2f(pid, key, vals.get(0), vals.get(1));
                    case 3 -> GlUtil.uniform3f(pid, key, vals.get(0), vals.get(1), vals.get(2));
                    case 4 -> GlUtil.uniform4f(pid, key, vals.get(0), vals.get(1), vals.get(2), vals.get(3));
                }
            }
        }
    }

    private static String loadResource(String path) {
        try (InputStream is = MultiMeshRenderer.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        return "";
    }
}
