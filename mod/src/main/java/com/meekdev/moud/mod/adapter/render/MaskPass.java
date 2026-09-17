package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.amnetic.client.render.Geometry;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.MeshPart;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.gl.RenderState;
import com.meekdev.moud.mod.client.ClientScene;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class MaskPass {

    private static final Identifier FULLSCREEN = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/fullscreen.vsh");
    private static final MeshData CUBE = MeshData.unitCube();

    private final String maskName;
    private final Identifier composite;
    private final float radius;
    private final String failure;
    private Framebuffer mask;
    private Framebuffer scene;
    private Framebuffer out;
    private ShaderProgram program;
    private boolean broken;

    public MaskPass(String maskName, Identifier composite, float radius, String failure) {
        this.maskName = maskName;
        this.composite = composite;
        this.radius = radius;
        this.failure = failure;
    }

    public boolean broken() {
        return broken;
    }

    public void run(Runnable pass) {
        try {
            pass.run();
        } catch (RuntimeException e) {
            broken = true;
            MoudMod.LOG.error(failure, e);
        } finally {
            RenderState.noPolygonOffset();
            RenderState.depthLessOrEqual();
            GlState.endFullscreen();
        }
    }

    public static void begin(Framebuffer target, boolean depthTested) {
        if (depthTested) target.blitDepthFromMain();
        target.begin();
        RenderState.clear(0.0f, 0.0f, 0.0f, 0.0f);
        RenderState.blend(false);
        RenderState.cull(false);
        RenderState.depthMask(false);
        if (depthTested) {
            RenderState.depthTest(true);
            RenderState.depthLessOrEqual();
            RenderState.polygonOffset();
        } else {
            RenderState.depthTest(false);
        }
    }

    public static void fill(Part part, Matrix4f projectionView, Vector3 eye, float partialTick, float r, float g, float b, float a) {
        if (part instanceof MeshPart meshPart) {
            Meshes.fillMask(meshPart, projectionView, eye, partialTick, r, g, b, a);
            return;
        }
        CFrame world = ClientScene.motion().sample(part, partialTick);
        Vector3 at = world.position().sub(eye);
        Quat turn = world.rotation();
        Matrix4f mvp = new Matrix4f(projectionView)
                .translate((float) at.x(), (float) at.y(), (float) at.z())
                .rotate(new Quaternionf((float) turn.x(), (float) turn.y(), (float) turn.z(), (float) turn.w()))
                .scale((float) part.size.x(), (float) part.size.y(), (float) part.size.z());
        Geometry.fill(CUBE, mvp, r, g, b, a);
    }

    public void beginMask(boolean depthTested) {
        ensureTargets();
        begin(mask, depthTested);
    }

    public void endMask() {
        mask.end();
        RenderState.noPolygonOffset();
    }

    public ShaderProgram beginComposite() {
        scene.blitColorFromMain();
        GlState.beginFullscreen();
        out.begin();
        GlState.bindTexture(0, scene.colorTextureGlId(0));
        GlState.bindTexture(1, mask.colorTextureGlId(0));
        program.begin();
        program.setSampler("SceneSampler", 0);
        program.setSampler("MaskSampler", 1);
        program.setVec2("Texel", 1.0f / mask.width(), 1.0f / mask.height());
        program.setFloat("Radius", radius);
        return program;
    }

    public void endComposite() {
        program.draw();
        out.end();
        out.blitColorToMain();
    }

    private void ensureTargets() {
        if (mask != null) return;
        mask = Framebuffers.screen(maskName, FramebufferSpec.builder().color(ColorFormat.RGBA8).depthTexture().build());
        scene = Framebuffers.captureColor();
        out = Framebuffers.captureColor();
        program = new ShaderProgram(FULLSCREEN, composite);
    }
}
