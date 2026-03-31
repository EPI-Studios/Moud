package com.moud.client.fabric.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.fabric.render.mesh.MoudMeshBuffer;
import com.moud.client.fabric.render.veil.GlUtil;
import com.moud.client.fabric.render.veil.VeilDynamicShaders;
import com.moud.net.protocol.SceneSnapshot;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class DecalRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DecalRenderer.class);

    private final MeshShaderRenderer meshShader;
    private final String vertSrc;
    private final String fragSrc;
    private ShaderProgram program;

    private int depthCopyFbo = 0;
    private int depthCopyTex = 0;
    private int depthCopyW = -1;
    private int depthCopyH = -1;

    private final Map<Long, Integer> vaoCache = new HashMap<>();

    DecalRenderer(MeshShaderRenderer meshShader) {
        this.meshShader = meshShader;
        vertSrc = loadResource("assets/moud/shaders/builtin/decal.vert");
        fragSrc = loadResource("assets/moud/shaders/builtin/decal.frag");
    }

    private static String loadResource(String path) {
        try (InputStream is = DecalRenderer.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        return "";
    }

    private ShaderProgram getProgram() {
        if (program != null && program.isValid()) return program;
        if (vertSrc.isEmpty() || fragSrc.isEmpty()) return null;
        Int2ObjectArrayMap<String> stages = new Int2ObjectArrayMap<>();
        stages.put(GL20C.GL_VERTEX_SHADER, vertSrc);
        stages.put(GL20C.GL_FRAGMENT_SHADER, fragSrc);
        program = VeilDynamicShaders.getOrCompile(Identifier.of("moud", "builtin/decal"), stages);
        return program;
    }

    void renderAll(List<SceneSnapshot.NodeSnapshot> nodes,
                   Function<Long, VeilSceneNodeRenderer.Pose> poseResolver,
                   Vec3d camPos, Camera camera, Matrix4fc viewMatrix, Matrix4fc projectionMatrix,
                   MinecraftClient client, float tickDelta) {
        List<SceneSnapshot.NodeSnapshot> decals = null;
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null || !"Decal".equals(node.type())) continue;
            if (!VeilSceneNodeRenderer.parseBool(VeilSceneNodeRenderer.stringProp(node, "visible"), true)) continue;
            if (decals == null) decals = new ArrayList<>();
            decals.add(node);
        }
        if (decals == null) return;

        ShaderProgram prog = getProgram();
        if (prog == null || !prog.isValid()) return;

        Matrix4f viewMat = viewMatrix != null ? new Matrix4f(viewMatrix) : new Matrix4f();
        Matrix4f projMat = projectionMatrix != null ? new Matrix4f(projectionMatrix) : new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f invViewProjMat = new Matrix4f(projMat).mul(viewMat).invert();

        Framebuffer mainFb = client.getFramebuffer();
        int sourceFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (sourceFbo == 0 && mainFb != null) {
            sourceFbo = mainFb.fbo;
        }

        int fbW = 0;
        int fbH = 0;
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        if (viewport[2] > 0 && viewport[3] > 0) {
            fbW = viewport[2];
            fbH = viewport[3];
        } else if (mainFb != null) {
            fbW = mainFb.textureWidth;
            fbH = mainFb.textureHeight;
        }
        if (sourceFbo == 0 || fbW <= 0 || fbH <= 0) {
            return;
        }
        ensureDepthCopy(fbW, fbH);
        if (depthCopyTex == 0) return;
        blitDepth(sourceFbo, fbW, fbH);

        MoudMeshBuffer.ensureInitialized();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        GL11.glDisable(GL11.GL_CULL_FACE);

        try {
            VeilRenderSystem.setShader(prog);
            prog.bind();

            int pid = GlUtil.currentProgram();
            GlUtil.uniformMat4(pid, "ViewMat", viewMat);
            GlUtil.uniformMat4(pid, "ProjMat", projMat);
            GlUtil.uniformMat4(pid, "InvViewProjMat", invViewProjMat);
            GlUtil.uniform3f(pid, "CameraPos", (float) camPos.x, (float) camPos.y, (float) camPos.z);
            meshShader.sceneLights().applyUniforms(pid);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthCopyTex);
            GlUtil.uniform1i(pid, "DepthSampler", 1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);

            for (SceneSnapshot.NodeSnapshot node : decals) {
                VeilSceneNodeRenderer.Pose world = poseResolver.apply(node.nodeId());
                if (world == null) continue;
                renderDecal(node, world, camPos, prog, pid);
            }
        } finally {
            ShaderProgram.unbind();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
        }
    }

    private void renderDecal(SceneSnapshot.NodeSnapshot node, VeilSceneNodeRenderer.Pose world,
                             Vec3d camPos, ShaderProgram prog, int pid) {
        float tintR  = VeilSceneNodeRenderer.clamp01(VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "color_tint_r"), 1f));
        float tintG  = VeilSceneNodeRenderer.clamp01(VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "color_tint_g"), 1f));
        float tintB  = VeilSceneNodeRenderer.clamp01(VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "color_tint_b"), 1f));
        float opacity = VeilSceneNodeRenderer.clamp01(VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "opacity"), 1f));

        Matrix4f modelMat = new Matrix4f()
                .translate((float)(world.pos.x - camPos.x),
                           (float)(world.pos.y - camPos.y),
                           (float)(world.pos.z - camPos.z))
                .rotate(world.rot)
                .scale(world.scale.x, world.scale.y, world.scale.z)
                .translate(-0.5f, -0.5f, -0.5f);

        Matrix4f worldMat = new Matrix4f()
                .translate(world.pos.x, world.pos.y, world.pos.z)
                .rotate(world.rot)
                .scale(world.scale.x, world.scale.y, world.scale.z)
                .translate(-0.5f, -0.5f, -0.5f);

        Matrix4f invDecalMat = new Matrix4f(worldMat).invert();

        GlUtil.uniformMat4(pid, "ModelMat", modelMat);
        GlUtil.uniformMat4(pid, "InvDecalMat", invDecalMat);
        GlUtil.uniform4f(pid, "Tint", tintR, tintG, tintB, opacity);

        Identifier texId = meshShader.resolveNodeTexture(node);
        int colorGlId = lookupGlTextureId(texId);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorGlId);
        GlUtil.uniform1i(pid, "DecalTexture", 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthCopyTex);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        int vbo = MoudMeshBuffer.vbo();
        int ebo = MoudMeshBuffer.ebo();
        int indexCount = MoudMeshBuffer.indexCount();
        long key = ((long) pid << 32) | (vbo & 0xFFFFFFFFL);
        int vao = vaoCache.computeIfAbsent(key, k -> GlUtil.createMeshVao(pid, vbo, ebo));
        GlUtil.drawElements(vao, indexCount);
    }

    private void ensureDepthCopy(int w, int h) {
        if (depthCopyFbo != 0 && depthCopyW == w && depthCopyH == h) return;

        if (depthCopyFbo != 0) {
            GL30.glDeleteFramebuffers(depthCopyFbo);
            GL11.glDeleteTextures(depthCopyTex);
            depthCopyFbo = 0;
            depthCopyTex = 0;
        }

        depthCopyTex = GL11.glGenTextures();
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthCopyTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT24, w, h, 0,
                GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);

        int prevFb = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevReadFb = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDrawFb = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        depthCopyFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, depthCopyFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, depthCopyTex, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFb);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFb);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFb);

        depthCopyW = w;
        depthCopyH = h;
    }

    private void blitDepth(int sourceFbo, int w, int h) {
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthCopyFbo);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h,
                GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
    }

    private static int lookupGlTextureId(Identifier id) {
        if (id == null) return 0;
        AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getTexture(id);
        return tex != null ? tex.getGlId() : 0;
    }

    void clear() {
        vaoCache.values().forEach(GlUtil::deleteVao);
        vaoCache.clear();
        program = null;
        if (depthCopyFbo != 0) {
            GL30.glDeleteFramebuffers(depthCopyFbo);
            GL11.glDeleteTextures(depthCopyTex);
            depthCopyFbo = 0;
            depthCopyTex = 0;
            depthCopyW = -1;
            depthCopyH = -1;
        }
    }
}
