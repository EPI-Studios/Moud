package com.moud.client.fabric.render.picking;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.fabric.render.VeilSceneNodeRenderer;
import com.moud.client.fabric.render.mesh.MoudMeshBuffer;
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
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.lwjgl.opengl.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public final class OutlineRenderer {

    private int maskFbo;
    private int maskColorTex;
    private int maskDepthTex;
    private int maskW;
    private int maskH;

    private int colorCopyTex;
    private int colorCopyW;
    private int colorCopyH;

    private int depthCopyTex;
    private int depthCopyW;
    private int depthCopyH;

    private final String maskVert;
    private final String maskFrag;
    private final String compositeVert;
    private final String compositeFrag;
    private ShaderProgram maskProgram;
    private ShaderProgram compositeProgram;
    private final Map<Long, Integer> vaoCache = new HashMap<>();

    public OutlineRenderer() {
        maskVert = loadResource("assets/moud/shaders/builtin/outline_mask.vert");
        maskFrag = loadResource("assets/moud/shaders/builtin/outline_mask.frag");
        compositeVert = loadResource("assets/moud/shaders/builtin/default_blit.vert");
        compositeFrag = loadResource("assets/moud/shaders/builtin/outline_composite.frag");
    }

    public void render(List<SceneSnapshot.NodeSnapshot> nodes,
                       Map<Long, SceneSnapshot.NodeSnapshot> nodesById,
                       Function<Long, VeilSceneNodeRenderer.Pose> poseResolver,
                       Vec3d camPos, Matrix4fc viewMatrix, Matrix4fc projectionMatrix,
                       long hoveredId, long selectedId,
                       MinecraftClient client) {

        if ((hoveredId <= 0 && selectedId <= 0) || client == null) return;

        Framebuffer mainFb = client.getFramebuffer();
        int sourceFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (sourceFbo == 0 && mainFb != null) sourceFbo = mainFb.fbo;

        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int fbW = viewport[2] > 0 ? viewport[2] : (mainFb != null ? mainFb.textureWidth : 0);
        int fbH = viewport[3] > 0 ? viewport[3] : (mainFb != null ? mainFb.textureHeight : 0);
        if (fbW <= 0 || fbH <= 0 || sourceFbo == 0) return;

        ShaderBlock<CameraMatrices> camBlock = VeilRenderSystem.getBlock(VeilShaderBufferRegistry.CAMERA.get());
        CameraMatrices veilCam = camBlock != null ? camBlock.getValue() : null;
        Matrix4f viewMat = veilCam != null ? new Matrix4f(veilCam.getViewMatrix())
                : (viewMatrix != null ? new Matrix4f(viewMatrix) : new Matrix4f());
        Matrix4f projMat = veilCam != null ? new Matrix4f(veilCam.getProjectionMatrix())
                : (projectionMatrix != null ? new Matrix4f(projectionMatrix) : new Matrix4f(RenderSystem.getProjectionMatrix()));

        ensureMaskFbo(fbW, fbH);
        if (maskFbo == 0) return;

        ensureColorCopy(fbW, fbH);
        if (colorCopyTex == 0) return;
        blitSceneColor(sourceFbo, fbW, fbH);

        ensureDepthCopy(fbW, fbH);
        if (depthCopyTex == 0) return;
        blitSceneDepth(sourceFbo, fbW, fbH);

        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, maskFbo);
        GL11.glViewport(0, 0, maskW, maskH);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        ShaderProgram mask = getMaskProgram();
        if (mask != null && mask.isValid()) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();

            MoudMeshBuffer.ensureInitialized();

            try {
                VeilRenderSystem.setShader(mask);
                mask.bind();
                int pid = GlUtil.currentProgram();
                GlUtil.uniformMat4(pid, "ViewMat", viewMat);
                GlUtil.uniformMat4(pid, "ProjMat", projMat);

                if (hoveredId > 0 && hoveredId != selectedId) {
                    renderMaskNode(nodesById.get(hoveredId), poseResolver, camPos, viewMat, pid, 0.5f);
                }

                if (selectedId > 0) {
                    renderMaskNode(nodesById.get(selectedId), poseResolver, camPos, viewMat, pid, 1.0f);
                }
            } finally {
                ShaderProgram.unbind();
            }
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

        ShaderProgram composite = getCompositeProgram();
        if (composite == null || !composite.isValid()) return;

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();

        try {
            VeilRenderSystem.setShader(composite);
            composite.bind();
            int pid = GlUtil.currentProgram();

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorCopyTex);
            GlUtil.uniform1i(pid, "DiffuseSampler0", 0);

            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthCopyTex);
            GlUtil.uniform1i(pid, "MainDepthSampler", 1);

            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskColorTex);
            GlUtil.uniform1i(pid, "OutlineMaskSampler", 2);

            GL13.glActiveTexture(GL13.GL_TEXTURE3);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskDepthTex);
            GlUtil.uniform1i(pid, "OutlineDepthSampler", 3);

            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            ShaderProgram.unbind();
            GL13.glActiveTexture(GL13.GL_TEXTURE3);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    private void renderMaskNode(SceneSnapshot.NodeSnapshot node,
                                Function<Long, VeilSceneNodeRenderer.Pose> poseResolver,
                                Vec3d camPos, Matrix4f viewMat, int pid, float maskAlpha) {
        if (node == null) return;
        String type = node.type();
        if (!NodePickingPass.isPickableType(type)) return;
        if (!VeilSceneNodeRenderer.parseBool(VeilSceneNodeRenderer.stringProp(node, "visible"), true)) return;

        VeilSceneNodeRenderer.Pose world = poseResolver.apply(node.nodeId());
        if (world == null) return;

        boolean billboard = VeilSceneNodeRenderer.parseBool(
                VeilSceneNodeRenderer.stringProp(node, "billboard"), "Sprite3D".equals(type));

        String meshType = VeilSceneNodeRenderer.stringProp(node, "mesh");
        if ("Sprite3D".equals(type) && (meshType == null || meshType.isBlank())) meshType = "plane";
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
        } else {
            modelMat = new Matrix4f()
                    .translate((float) (world.pos.x - camPos.x),
                            (float) (world.pos.y - camPos.y),
                            (float) (world.pos.z - camPos.z))
                    .rotate(world.rot)
                    .scale(world.scale.x, world.scale.y, world.scale.z);
        }
        if (isPlane) {
            modelMat.rotateX(-HALF_PI).translate(-0.5f, 0.0f, -0.5f);
        } else {
            modelMat.translate(-0.5f, -0.5f, -0.5f);
        }

        GlUtil.uniformMat4(pid, "ModelMat", modelMat);
        GlUtil.uniform1f(pid, "MaskAlpha", maskAlpha);

        int vbo, ebo, indexCount;
        if ("plane".equals(meshType)) {
            MoudMeshBuffer.ensurePlaneInitialized();
            vbo = MoudMeshBuffer.planeVbo();
            ebo = MoudMeshBuffer.planeEbo();
            indexCount = MoudMeshBuffer.planeIndexCount();
        } else if ("sphere".equals(meshType)) {
            MoudMeshBuffer.ensureSphereInitialized();
            vbo = MoudMeshBuffer.sphereVbo();
            ebo = MoudMeshBuffer.sphereEbo();
            indexCount = MoudMeshBuffer.sphereIndexCount();
        } else {
            vbo = MoudMeshBuffer.vbo();
            ebo = MoudMeshBuffer.ebo();
            indexCount = MoudMeshBuffer.indexCount();
        }

        long key = ((long) pid << 32) | (vbo & 0xFFFFFFFFL);
        int vao = vaoCache.computeIfAbsent(key, k -> GlUtil.createMeshVao(pid, vbo, ebo));
        GlUtil.drawElements(vao, indexCount);
    }

    private void ensureMaskFbo(int w, int h) {
        if (maskFbo != 0 && maskW == w && maskH == h) return;
        destroyMaskFbo();

        maskFbo = GL30.glGenFramebuffers();
        maskColorTex = GL11.glGenTextures();
        maskDepthTex = GL11.glGenTextures();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskColorTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskDepthTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT24, w, h, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, maskFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, maskColorTex, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, maskDepthTex, 0);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);

        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            destroyMaskFbo();
            return;
        }
        maskW = w;
        maskH = h;
    }

    private void destroyMaskFbo() {
        if (maskFbo != 0) { GL30.glDeleteFramebuffers(maskFbo); maskFbo = 0; }
        if (maskColorTex != 0) { GL11.glDeleteTextures(maskColorTex); maskColorTex = 0; }
        if (maskDepthTex != 0) { GL11.glDeleteTextures(maskDepthTex); maskDepthTex = 0; }
        maskW = 0;
        maskH = 0;
    }

    private void ensureDepthCopy(int w, int h) {
        if (depthCopyTex != 0 && depthCopyW == w && depthCopyH == h) return;
        if (depthCopyTex != 0) GL11.glDeleteTextures(depthCopyTex);

        depthCopyTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthCopyTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT24, w, h, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        depthCopyW = w;
        depthCopyH = h;
    }

    private void ensureColorCopy(int w, int h) {
        if (colorCopyTex != 0 && colorCopyW == w && colorCopyH == h) return;
        if (colorCopyTex != 0) GL11.glDeleteTextures(colorCopyTex);

        colorCopyTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorCopyTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        colorCopyW = w;
        colorCopyH = h;
    }

    private void blitSceneColor(int sourceFbo, int w, int h) {
        int copyFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copyFbo);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorCopyTex, 0);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFbo);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sourceFbo);
        GL30.glDeleteFramebuffers(copyFbo);
    }

    private void blitSceneDepth(int sourceFbo, int w, int h) {
        int copyFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copyFbo);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthCopyTex, 0);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFbo);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sourceFbo);
        GL30.glDeleteFramebuffers(copyFbo);
    }

    private ShaderProgram getMaskProgram() {
        if (maskProgram != null && maskProgram.isValid()) return maskProgram;
        if (maskVert.isEmpty() || maskFrag.isEmpty()) return null;
        Int2ObjectMap<String> stages = new Int2ObjectArrayMap<>();
        stages.put(GL20.GL_VERTEX_SHADER, maskVert);
        stages.put(GL20.GL_FRAGMENT_SHADER, maskFrag);
        maskProgram = VeilDynamicShaders.getOrCompile(Identifier.of("moud", "builtin/outline_mask"), stages);
        return maskProgram;
    }

    private ShaderProgram getCompositeProgram() {
        if (compositeProgram != null && compositeProgram.isValid()) return compositeProgram;
        if (compositeVert.isEmpty() || compositeFrag.isEmpty()) return null;
        Int2ObjectMap<String> stages = new Int2ObjectArrayMap<>();
        stages.put(GL20.GL_VERTEX_SHADER, compositeVert);
        stages.put(GL20.GL_FRAGMENT_SHADER, compositeFrag);
        compositeProgram = VeilDynamicShaders.getOrCompile(Identifier.of("moud", "builtin/outline_composite"), stages);
        return compositeProgram;
    }

    public void clear() {
        for (int vao : vaoCache.values()) GlUtil.deleteVao(vao);
        vaoCache.clear();
        destroyMaskFbo();
        if (colorCopyTex != 0) { GL11.glDeleteTextures(colorCopyTex); colorCopyTex = 0; }
        colorCopyW = 0;
        colorCopyH = 0;
        if (depthCopyTex != 0) { GL11.glDeleteTextures(depthCopyTex); depthCopyTex = 0; }
        depthCopyW = 0;
        depthCopyH = 0;
        maskProgram = null;
        compositeProgram = null;
    }

    private static String loadResource(String path) {
        try (InputStream is = OutlineRenderer.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        return "";
    }
}
