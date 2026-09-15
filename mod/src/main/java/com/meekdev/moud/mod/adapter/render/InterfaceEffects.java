package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.moud.core.render.post.ScreenEffect;
import com.meekdev.moud.mod.adapter.gl.RenderState;
import java.util.List;
import org.joml.Matrix4f;

public final class InterfaceEffects {

    public record Scoped(ScreenEffect effect, float x, float y, float width, float height) {}

    private static final long START = System.nanoTime();
    private static final Matrix4f IDENTITY = new Matrix4f();

    private static Framebuffer scratch;
    private static Framebuffer farDepth;

    private InterfaceEffects() {}

    public record Eye(double x, double y, double z, Matrix4f viewProj, Matrix4f invViewProj, boolean zeroToOne) {}

    public static void apply(Framebuffer canvas, List<Scoped> effects) {
        if (effects.isEmpty()) return;
        ensure(canvas.width(), canvas.height());
        CameraSnapshot camera = CameraSnapshot.current();
        Eye eye = camera == null ? new Eye(0, 0, 0, IDENTITY, IDENTITY, false)
                : new Eye(camera.eye.x, camera.eye.y, camera.eye.z, camera.viewProj, camera.invViewProj, camera.zeroToOne);
        apply(canvas, farDepth.colorTextureGlId(0), eye, effects);
    }

    public static void apply(Framebuffer canvas, int depthTexture, Eye eye, List<Scoped> effects) {
        if (effects.isEmpty()) return;
        ensure(canvas.width(), canvas.height());
        float time = (float) (((System.nanoTime() - START) / 1.0e9) % 3600.0);
        for (Scoped scoped : effects) {
            ScreenShader program = ScreenShader.of(scoped.effect());
            ScreenShader copy = ScreenShader.copy();
            if (program == null || program.broken() || copy.broken()) continue;
            GlState.beginFullscreen();
            try {
                scratch.begin();
                RenderState.clear(0, 0, 0, 0);
                GlState.bindTexture(0, canvas.colorTextureGlId(0));
                GlState.bindTexture(1, depthTexture);
                ShaderProgram shader = program.program;
                shader.begin();
                shader.setSampler("SceneColorSampler", 0);
                shader.setSampler("SceneDepthSampler", 1);
                shader.setVec2("ScreenSize", canvas.width(), canvas.height());
                shader.setFloat("Time", time);
                shader.setMatrix4("ViewProj", eye.viewProj());
                shader.setMatrix4("InvViewProj", eye.invViewProj());
                shader.setMatrix4("PrevViewProj", eye.viewProj());
                shader.setVec3("CameraPosition", (float) eye.x(), (float) eye.y(), (float) eye.z());
                shader.setVec3("PrevCameraPosition", (float) eye.x(), (float) eye.y(), (float) eye.z());
                shader.setInt("ZeroToOne", eye.zeroToOne() ? 1 : 0);
                PostStack.uniforms(scoped.effect(), shader);
                shader.draw();
                scratch.end();

                canvas.begin();
                int left = Math.max(0, Math.round(scoped.x()));
                int top = Math.max(0, Math.round(scoped.y()));
                int right = Math.min(canvas.width(), Math.round(scoped.x() + scoped.width()));
                int bottom = Math.min(canvas.height(), Math.round(scoped.y() + scoped.height()));
                if (right > left && bottom > top) {
                    RenderState.scissor(left, canvas.height() - bottom, right - left, bottom - top);
                    RenderState.colorMask(true, true, true, false);
                    GlState.bindTexture(0, scratch.colorTextureGlId(0));
                    ShaderProgram back = copy.program;
                    back.begin();
                    back.setSampler("SceneColorSampler", 0);
                    back.draw();
                    RenderState.colorMask(true, true, true, true);
                    RenderState.noScissor();
                }
                canvas.end();
            } catch (RuntimeException e) {
                program.fail(scoped.effect().name(), e);
                RenderState.colorMask(true, true, true, true);
                RenderState.noScissor();
            } finally {
                GlState.endFullscreen();
            }
        }
    }

    private static void ensure(int width, int height) {
        if (scratch != null && (scratch.width() != width || scratch.height() != height)) {
            scratch.dispose();
            scratch = null;
        }
        if (scratch == null) scratch = Framebuffers.fixed(width, height, FramebufferSpec.builder().color(ColorFormat.RGBA8).build());
        if (farDepth == null) {
            farDepth = Framebuffers.fixed(1, 1, FramebufferSpec.builder().color(ColorFormat.R8).build());
            farDepth.begin();
            farDepth.clear(1, 1, 1, 1);
            farDepth.end();
        }
    }
}
