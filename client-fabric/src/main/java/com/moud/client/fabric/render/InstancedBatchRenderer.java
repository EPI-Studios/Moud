package com.moud.client.fabric.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.fabric.render.mesh.MoudMeshBuffer;
import com.moud.client.fabric.render.MoudTextures;
import com.moud.client.fabric.render.veil.GlUtil;
import com.moud.client.fabric.render.veil.VeilDynamicShaders;
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

    InstancedBatchRenderer(SceneLights sceneLights) {
        this.sceneLights = sceneLights;
        instancedVert = loadResource("assets/moud/shaders/builtin/default_mesh_instanced.vert");
        instancedFrag = loadResource("assets/moud/shaders/builtin/default_mesh_instanced.frag");
    }

    int renderBatched(List<SceneSnapshot.NodeSnapshot> nodes,
                      Function<Long, VeilSceneNodeRenderer.Pose> poseResolver,
                      Vec3d camPos, Camera camera, Matrix4fc viewMatrix, Matrix4fc projectionMatrix,
                      MinecraftClient client, float tickDelta) {
        if (!RenderSystem.isOnRenderThread()) return 0;

        ShaderProgram program = getOrCompileProgram();
        if (program == null || !program.isValid()) return 0;

        MoudMeshBuffer.ensureInitialized();

        Map<String, List<NodeInstance>> batches = buildBatches(nodes, poseResolver, camPos);
        if (batches.isEmpty()) return 0;

        ShaderBlock<CameraMatrices> camBlock = VeilRenderSystem.getBlock(VeilShaderBufferRegistry.CAMERA.get());
        CameraMatrices veilCam = camBlock != null ? camBlock.getValue() : null;
        Matrix4f viewMat = veilCam != null ? new Matrix4f(veilCam.getViewMatrix())
                : (viewMatrix != null ? new Matrix4f(viewMatrix) : new Matrix4f());
        Matrix4f projMat = veilCam != null ? new Matrix4f(veilCam.getProjectionMatrix())
                : (projectionMatrix != null ? new Matrix4f(projectionMatrix) : new Matrix4f(RenderSystem.getProjectionMatrix()));

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        int rendered = 0;

        try {
            VeilRenderSystem.setShader(program);
            program.bind();

            int pid = GlUtil.currentProgram();
            uploadFrameUniforms(pid, viewMat, projMat, camPos, client, tickDelta);

            program.clearSamplers();
            program.setSampler("Texture0", MoudTextures.white());
            program.bindSamplers(0);

            for (var entry : batches.entrySet()) {
                List<NodeInstance> instances = entry.getValue();
                if (instances.isEmpty()) continue;

                String batchKey = entry.getKey();
                boolean doubleSided = batchKey.endsWith(":ds");
                String meshKey = doubleSided ? batchKey.substring(0, batchKey.length() - 3) : batchKey;

                var mesh = resolveMesh(meshKey);
                ensureInstanceVbo(instances.size());
                fillInstanceData(instances);
                uploadInstanceData();

                long key = ((long) pid << 32) | (mesh.vbo & 0xFFFFFFFFL);
                int vao = vaoCache.computeIfAbsent(key,
                        k -> GlUtil.createInstancedMeshVao(pid, mesh.vbo, mesh.ebo, instanceVbo));

                if (doubleSided) RenderSystem.disableCull();
                GlUtil.drawElementsInstanced(vao, mesh.indexCount, instances.size());
                if (doubleSided) RenderSystem.enableCull();
                rendered += instances.size();
            }
        } finally {
            ShaderProgram.unbind();
        }

        return rendered;
    }

    private Map<String, List<NodeInstance>> buildBatches(List<SceneSnapshot.NodeSnapshot> nodes,
                                                         Function<Long, VeilSceneNodeRenderer.Pose> poseResolver,
                                                         Vec3d camPos) {
        Map<String, List<NodeInstance>> batches = new HashMap<>();
        for (var node : nodes) {
            if (node == null) continue;
            String type = node.type();
            if (!"MeshInstance3D".equals(type) && !"CSGBox".equals(type)) continue;
            if (!VeilSceneNodeRenderer.parseBool(VeilSceneNodeRenderer.stringProp(node, "visible"), true)) continue;

            String materialPath = VeilSceneNodeRenderer.stringProp(node, "material");
            if (materialPath != null && !materialPath.isBlank()) continue;

            String texProp = VeilSceneNodeRenderer.stringProp(node, "texture");
            boolean hasCustomTexture = texProp != null && !texProp.isBlank()
                    && !MoudTextures.WHITE_ID.toString().equals(texProp)
                    && !"moud:dynamic/white".equals(texProp);
            if (hasCustomTexture) continue;

            VeilSceneNodeRenderer.Pose world = poseResolver.apply(node.nodeId());
            if (world == null) continue;

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
                    .add(new NodeInstance(world, tintR, tintG, tintB, opacity));
        }
        return batches;
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
    }

    private record MeshHandles(int vbo, int ebo, int indexCount) {}

    private static MeshHandles resolveMesh(String meshType) {
        return switch (meshType) {
            case "plane" -> {
                MoudMeshBuffer.ensurePlaneInitialized();
                yield new MeshHandles(MoudMeshBuffer.planeVbo(), MoudMeshBuffer.planeEbo(), MoudMeshBuffer.planeIndexCount());
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

    private void fillInstanceData(List<NodeInstance> instances) {
        instanceBuffer.clear();
        for (var inst : instances) {
            inst.worldMat.get(instanceBuffer);
            instanceBuffer.position(instanceBuffer.position() + 16);
            instanceBuffer.put(inst.tintR).put(inst.tintG).put(inst.tintB).put(inst.opacity);
        }
        instanceBuffer.flip();
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

    private record NodeInstance(Matrix4f worldMat,
                                float tintR, float tintG, float tintB, float opacity) {
        NodeInstance(VeilSceneNodeRenderer.Pose world,
                     float tintR, float tintG, float tintB, float opacity) {
            this(
                    new Matrix4f()
                            .translate(world.pos.x, world.pos.y, world.pos.z)
                            .rotate(world.rot)
                            .scale(world.scale.x, world.scale.y, world.scale.z)
                            .translate(-0.5f, -0.5f, -0.5f),
                    tintR, tintG, tintB, opacity
            );
        }
    }
}
