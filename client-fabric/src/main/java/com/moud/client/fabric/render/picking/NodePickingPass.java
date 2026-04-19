package com.moud.client.fabric.render.picking;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.fabric.render.Model3DRenderer;
import com.moud.client.fabric.render.VeilSceneNodeRenderer;
import com.moud.client.fabric.render.scene.math.Pose;
import com.moud.client.fabric.render.mesh.MoudMeshBuffer;
import com.moud.client.fabric.render.veil.GlUtil;
import com.moud.client.fabric.render.veil.VeilDynamicShaders;
import com.moud.core.csg.CsgVoxelizer;
import com.moud.net.protocol.SceneSnapshot;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public final class NodePickingPass {

    private int fbo;
    private int colorTex;
    private int depthRbo;
    private int fboWidth;
    private int fboHeight;

    private final String pickVert;
    private final String pickFrag;
    private final String pickInstancedVert;
    private final String pickInstancedFrag;
    private ShaderProgram pickProgram;
    private ShaderProgram pickInstancedProgram;
    private final Map<Long, Integer> vaoCache = new HashMap<>();
    private int instanceVbo;
    private int instanceVboCapacity;
    private FloatBuffer instanceBuffer;

    private static final int FLOATS_PER_INSTANCE = 20;

    private long hoveredNodeId;

    public NodePickingPass() {
        pickVert = loadResource("assets/moud/shaders/builtin/node_pick.vert");
        pickFrag = loadResource("assets/moud/shaders/builtin/node_pick.frag");
        pickInstancedVert = loadResource("assets/moud/shaders/builtin/node_pick_instanced.vert");
        pickInstancedFrag = loadResource("assets/moud/shaders/builtin/node_pick_instanced.frag");
    }

    public long hoveredNodeId() {
        return hoveredNodeId;
    }

    public void render(List<SceneSnapshot.NodeSnapshot> nodes,
                       Function<Long, Pose> poseResolver,
                       Vec3d camPos, Matrix4fc viewMatrix, Matrix4fc projectionMatrix,
                       int viewportW, int viewportH,
                       float mouseNdcX, float mouseNdcY) {

        if (nodes.isEmpty() || viewportW <= 0 || viewportH <= 0) {
            hoveredNodeId = 0L;
            return;
        }

        ensureFbo(viewportW, viewportH);
        if (fbo == 0) {
            hoveredNodeId = 0L;
            return;
        }

        Matrix4f viewMat = viewMatrix != null ? new Matrix4f(viewMatrix) : new Matrix4f();
        Matrix4f projMat = projectionMatrix != null ? new Matrix4f(projectionMatrix) : new Matrix4f(RenderSystem.getProjectionMatrix());

        int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
            GL11.glViewport(0, 0, fboWidth, fboHeight);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();

            Map<String, List<PickInstance>> batches = new HashMap<>();

            for (SceneSnapshot.NodeSnapshot node : nodes) {
                if (node == null) continue;
                if (!VeilSceneNodeRenderer.parseBool(VeilSceneNodeRenderer.stringProp(node, "visible"), true)) continue;

                String type = node.type();
                if (!isPickableType(type)) continue;

                if ("CSGBlock".equals(type)) {
                    collectCsgBlockPickInstances(node, camPos, batches);
                    continue;
                }

                Pose world = poseResolver.apply(node.nodeId());
                if (world == null) continue;

                 if ("Model3D".equals(type)) {
                    collectModelPickInstances(node, world, camPos, batches);
                    continue;
                }

                boolean billboard = VeilSceneNodeRenderer.parseBool(
                        VeilSceneNodeRenderer.stringProp(node, "billboard"), "Sprite3D".equals(type) || "AnimatedSprite3D".equals(type));

                String meshType = VeilSceneNodeRenderer.stringProp(node, "mesh");
                if (("Sprite3D".equals(type) || "AnimatedSprite3D".equals(type)) && (meshType == null || meshType.isBlank())) meshType = "plane";
                if (meshType == null || meshType.isBlank()) meshType = "cube";
                boolean isPlane = "plane".equals(meshType);
                float HALF_PI = (float) (Math.PI / 2.0);

                Matrix4f modelMat;
                if (billboard) {
                    Quaternionf camRot = viewMat.getNormalizedRotation(new Quaternionf()).conjugate();
                    modelMat = new Matrix4f()
                            .translate((float) (world.pos.x - camPos.x),
                                    (float) (world.pos.y - camPos.y),
                                    (float) (world.pos.z - camPos.z))
                            .rotate(camRot)
                            .scale(world.scale.x, world.scale.y, world.scale.z);
                    if (isPlane) {
                        modelMat.rotateX(-HALF_PI).translate(-0.5f, 0.0f, -0.5f);
                    } else {
                        modelMat.translate(-0.5f, -0.5f, -0.5f);
                    }
                } else {
                    modelMat = new Matrix4f()
                            .translate((float) (world.pos.x - camPos.x),
                                    (float) (world.pos.y - camPos.y),
                                    (float) (world.pos.z - camPos.z))
                            .rotate(world.rot)
                            .scale(world.scale.x, world.scale.y, world.scale.z);
                    if (isPlane) {
                        modelMat.rotateX(-HALF_PI).translate(-0.5f, 0.0f, -0.5f);
                    } else {
                        modelMat.translate(-0.5f, -0.5f, -0.5f);
                    }
                }

                PickInstance inst = new PickInstance(node.nodeId(), modelMat, meshType);
                batches.computeIfAbsent(meshType, k -> new ArrayList<>()).add(inst);
            }

            renderBatchesPick(batches, viewMat, projMat);

            int px = Math.round((mouseNdcX * 0.5f + 0.5f) * fboWidth);
            int py = Math.round((mouseNdcY * 0.5f + 0.5f) * fboHeight);
            px = Math.max(0, Math.min(fboWidth - 1, px));
            py = Math.max(0, Math.min(fboHeight - 1, py));

            ByteBuffer pixel = MemoryUtil.memAlloc(4);
            try {
                GL11.glReadPixels(px, py, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
                int r = pixel.get(0) & 0xFF;
                int g = pixel.get(1) & 0xFF;
                int b = pixel.get(2) & 0xFF;
                int a = pixel.get(3) & 0xFF;
                hoveredNodeId = (a == 0) ? 0L : ((long) r | ((long) g << 8) | ((long) b << 16));
            } finally {
                MemoryUtil.memFree(pixel);
            }
        } finally {
            GL30.glBindVertexArray(prevVao);
            ShaderProgram.unbind();
            GL13.glActiveTexture(prevActiveTexture);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (prevDepthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(true);
            if (prevBlend) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
            if (prevCull) RenderSystem.enableCull();
            else RenderSystem.disableCull();
        }
    }

    private void collectCsgBlockPickInstances(SceneSnapshot.NodeSnapshot node,
                                              Vec3d camPos,
                                              Map<String, List<PickInstance>> batches) {
        CsgVoxelizer.VoxelDefinition def = csgVoxelDefinition(node);
        if (def == null) {
            return;
        }
        List<PickInstance> cubeBatch = batches.computeIfAbsent("cube", k -> new ArrayList<>());
        CsgVoxelizer.forEachVoxel(def, (x, y, z) -> cubeBatch.add(new PickInstance(
                node.nodeId(),
                cubeModelAt(x + 0.5f - (float) camPos.x, y + 0.5f - (float) camPos.y, z + 0.5f - (float) camPos.z),
                "cube"
        )));
    }

    private void collectModelPickInstances(SceneSnapshot.NodeSnapshot node,
                                           Pose world,
                                           Vec3d camPos,
                                           Map<String, List<PickInstance>> batches) {
        List<PickInstance> cubeBatch = batches.computeIfAbsent("cube", k -> new ArrayList<>());
        Matrix4f nodeTransform = new Matrix4f()
                .translate((float) (world.pos.x - camPos.x),
                        (float) (world.pos.y - camPos.y),
                        (float) (world.pos.z - camPos.z))
                .rotate(world.rot)
                .scale(world.scale.x, world.scale.y, world.scale.z);
        Model3DRenderer.forEachCubeTransform(node, cubeMatrix ->
                cubeBatch.add(new PickInstance(node.nodeId(), new Matrix4f(nodeTransform).mul(cubeMatrix), "cube")));
    }

    private static Matrix4f cubeModelAt(float centerX, float centerY, float centerZ) {
        return new Matrix4f()
                .translate(centerX, centerY, centerZ)
                .translate(-0.5f, -0.5f, -0.5f);
    }

    private static CsgVoxelizer.VoxelDefinition csgVoxelDefinition(SceneSnapshot.NodeSnapshot node) {
        if (node == null) {
            return null;
        }
        int x = Math.round(propFloat(node, "x", 0.0f));
        int y = Math.round(propFloat(node, "y", 0.0f));
        int z = Math.round(propFloat(node, "z", 0.0f));
        int sx = Math.max(1, Math.round(propFloat(node, "sx", 1.0f)));
        int sy = Math.max(1, Math.round(propFloat(node, "sy", 1.0f)));
        int sz = Math.max(1, Math.round(propFloat(node, "sz", 1.0f)));
        float rx = propFloat(node, "rx", 0.0f);
        float ry = propFloat(node, "ry", 0.0f);
        float rz = propFloat(node, "rz", 0.0f);
        return new CsgVoxelizer.VoxelDefinition(x, y, z, sx, sy, sz, rx, ry, rz);
    }

    private static float propFloat(SceneSnapshot.NodeSnapshot node, String key, float fallback) {
        return VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, key), fallback);
    }

    private void renderBatchesPick(Map<String, List<PickInstance>> batches, Matrix4f viewMat, Matrix4f projMat) {
        if (batches.isEmpty()) return;

        ShaderProgram program = getPickProgram();
        if (program == null || !program.isValid()) return;

        MoudMeshBuffer.ensureInitialized();

        try {
            VeilRenderSystem.setShader(program);
            program.bind();

            int pid = GlUtil.currentProgram();
            GlUtil.uniformMat4(pid, "ViewMat", viewMat);
            GlUtil.uniformMat4(pid, "ProjMat", projMat);

            for (var entry : batches.entrySet()) {
                int vbo, ebo, indexCount;
                switch (entry.getKey()) {
                    case "plane" -> {
                        MoudMeshBuffer.ensurePlaneInitialized();
                        vbo = MoudMeshBuffer.planeVbo();
                        ebo = MoudMeshBuffer.planeEbo();
                        indexCount = MoudMeshBuffer.planeIndexCount();
                    }
                    case "sphere" -> {
                        MoudMeshBuffer.ensureSphereInitialized();
                        vbo = MoudMeshBuffer.sphereVbo();
                        ebo = MoudMeshBuffer.sphereEbo();
                        indexCount = MoudMeshBuffer.sphereIndexCount();
                    }
                    default -> {
                        vbo = MoudMeshBuffer.vbo();
                        ebo = MoudMeshBuffer.ebo();
                        indexCount = MoudMeshBuffer.indexCount();
                    }
                }

                for (PickInstance inst : entry.getValue()) {
                    float r = ((inst.nodeId) & 0xFF) / 255.0f;
                    float g = ((inst.nodeId >> 8) & 0xFF) / 255.0f;
                    float b = ((inst.nodeId >> 16) & 0xFF) / 255.0f;

                    GlUtil.uniformMat4(pid, "ModelMat", inst.modelMat);
                    GlUtil.uniform4f(pid, "PickColor", r, g, b, 1.0f);

                    long key = ((long) pid << 32) | (vbo & 0xFFFFFFFFL);
                    int vao = vaoCache.computeIfAbsent(key, k -> GlUtil.createMeshVao(pid, vbo, ebo));
                    GlUtil.drawElements(vao, indexCount);
                }
            }
        } finally {
            ShaderProgram.unbind();
        }
    }

    private void ensureFbo(int w, int h) {
        if (fbo != 0 && fboWidth == w && fboHeight == h) return;
        destroyFbo();

        fbo = GL30.glGenFramebuffers();
        colorTex = GL11.glGenTextures();
        depthRbo = GL30.glGenRenderbuffers();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRbo);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, w, h);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);

        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTex, 0);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRbo);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);

        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            destroyFbo();
            return;
        }

        fboWidth = w;
        fboHeight = h;
    }

    private void destroyFbo() {
        if (fbo != 0) { GL30.glDeleteFramebuffers(fbo); fbo = 0; }
        if (colorTex != 0) { GL11.glDeleteTextures(colorTex); colorTex = 0; }
        if (depthRbo != 0) { GL30.glDeleteRenderbuffers(depthRbo); depthRbo = 0; }
        fboWidth = 0;
        fboHeight = 0;
    }

    private ShaderProgram getPickProgram() {
        if (pickProgram != null && pickProgram.isValid()) return pickProgram;
        if (pickVert.isEmpty() || pickFrag.isEmpty()) return null;
        Int2ObjectMap<String> stages = new Int2ObjectArrayMap<>();
        stages.put(GL20.GL_VERTEX_SHADER, pickVert);
        stages.put(GL20.GL_FRAGMENT_SHADER, pickFrag);
        pickProgram = VeilDynamicShaders.getOrCompile(Identifier.of("moud", "builtin/node_pick"), stages);
        return pickProgram;
    }

    public void clear() {
        for (int vao : vaoCache.values()) GlUtil.deleteVao(vao);
        vaoCache.clear();
        destroyFbo();
        if (instanceVbo != 0) { GL15.glDeleteBuffers(instanceVbo); instanceVbo = 0; }
        if (instanceBuffer != null) { MemoryUtil.memFree(instanceBuffer); instanceBuffer = null; }
        pickProgram = null;
        pickInstancedProgram = null;
        hoveredNodeId = 0L;
    }

    static boolean isPickableType(String type) {
        return switch (type) {
            case "MeshInstance3D", "CSGBox", "CSGBlock", "Sprite3D", "AnimatedSprite3D", "Model3D", "MultiMesh3D" -> true;
            default -> false;
        };
    }

    private record PickInstance(long nodeId, Matrix4f modelMat, String meshType) {}

    private static String loadResource(String path) {
        try (InputStream is = NodePickingPass.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        return "";
    }
}
