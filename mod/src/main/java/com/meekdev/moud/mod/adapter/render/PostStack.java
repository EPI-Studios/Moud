package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.render.post.PostEffect;
import com.meekdev.moud.core.render.post.PostShader;
import com.meekdev.moud.core.render.post.ScreenEffect;
import com.meekdev.moud.core.ui.BillboardGui;
import com.meekdev.moud.core.ui.SurfaceGui;
import com.meekdev.moud.core.value.BoolValue;
import com.meekdev.moud.core.value.NumberValue;
import com.meekdev.moud.core.value.Vector3Value;
import com.meekdev.moud.mod.client.ClientScene;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.joml.Matrix4f;

public final class PostStack {

    private static final List<ScreenEffect> FRAME = new ArrayList<>();
    private static final Matrix4f PREV_VIEW_PROJ = new Matrix4f();
    private static final long START = System.nanoTime();

    private static Framebuffer color;
    private static Framebuffer depth;
    private static Framebuffer out;
    private static Vector3 prevEye = Vector3.ZERO;
    private static boolean hasPrev;

    private PostStack() {}

    public static void register() {
        EffectSettings.captureDefaults();
        Pipeline.add(RenderStage.POST, 15, "moud screen effects", ctx -> draw());
    }

    public static void frame() {
        InstanceTree tree = ClientScene.tree();
        FRAME.clear();
        List<PostEffect> settings = new ArrayList<>();
        if (tree != null) {
            for (PostEffect effect : tree.ofClass(Classes.POST_EFFECT)) {
                if (!effect.enabled) continue;
                if (insideInterface(effect)) continue;
                if (!(effect instanceof ScreenEffect screen)) settings.add(effect);
                else if (screen.intensity > 0) FRAME.add(screen);
            }
        }
        FRAME.sort(Comparator.comparingDouble(effect -> effect.order));
        EffectSettings.applyOnly(settings);
    }

    public static boolean insideInterface(Instance effect) {
        for (Instance at = effect.parent(); at != null; at = at.parent()) {
            if (at instanceof SurfaceGui || at instanceof BillboardGui) return true;
        }
        return false;
    }

    private static void draw() {
        if (FRAME.isEmpty()) return;
        CameraSnapshot camera = CameraSnapshot.current();
        if (camera == null) return;
        if (color == null) {
            color = Framebuffers.captureColor();
            depth = Framebuffers.captureDepth();
            out = Framebuffers.captureColor();
        }
        depth.blitDepthFromMain();
        float time = (float) (((System.nanoTime() - START) / 1.0e9) % 3600.0);
        if (!hasPrev) {
            PREV_VIEW_PROJ.set(camera.viewProj);
            prevEye = new Vector3(camera.eye.x, camera.eye.y, camera.eye.z);
        }
        for (ScreenEffect effect : FRAME) {
            ScreenShader program = ScreenShader.of(effect);
            if (program == null || program.broken()) continue;
            color.blitColorFromMain();
            GlState.beginFullscreen();
            try {
                out.begin();
                GlState.bindTexture(0, color.colorTextureGlId(0));
                GlState.bindTexture(1, depth.depthTextureGlId());
                ShaderProgram shader = program.program;
                shader.begin();
                shader.setSampler("SceneColorSampler", 0);
                shader.setSampler("SceneDepthSampler", 1);
                shader.setVec2("ScreenSize", color.width(), color.height());
                shader.setFloat("Time", time);
                shader.setVec3("CameraPosition", (float) camera.eye.x, (float) camera.eye.y, (float) camera.eye.z);
                shader.setVec3("PrevCameraPosition", (float) prevEye.x(), (float) prevEye.y(), (float) prevEye.z());
                shader.setMatrix4("ViewProj", camera.viewProj);
                shader.setMatrix4("InvViewProj", camera.invViewProj);
                shader.setMatrix4("PrevViewProj", PREV_VIEW_PROJ);
                shader.setInt("ZeroToOne", camera.zeroToOne ? 1 : 0);
                uniforms(effect, shader);
                shader.draw();
                out.end();
                out.blitColorToMain();
            } catch (RuntimeException e) {
                program.fail(effect.name(), e);
            } finally {
                GlState.endFullscreen();
            }
        }
        PREV_VIEW_PROJ.set(camera.viewProj);
        prevEye = new Vector3(camera.eye.x, camera.eye.y, camera.eye.z);
        hasPrev = true;
    }

    static void uniforms(ScreenEffect effect, ShaderProgram shader) {
        for (PropertyDef property : effect.def().properties()) {
            String name = Character.toUpperCase(property.name().charAt(0)) + property.name().substring(1);
            switch (property.type()) {
                case BOOL -> shader.setInt(name, property.getBool(effect) ? 1 : 0);
                case INT, NUM -> shader.setFloat(name, (float) property.getNum(effect));
                case VEC3 -> {
                    Vector3 v = (Vector3) property.getObj(effect);
                    shader.setVec3(name, (float) v.x(), (float) v.y(), (float) v.z());
                }
                case COLOR -> {
                    Color c = (Color) property.getObj(effect);
                    shader.setVec4(name, c.r(), c.g(), c.b(), c.a());
                }
                case ENUM -> shader.setInt(name, ((Enum<?>) property.getObj(effect)).ordinal());
                default -> {}
            }
        }
        if (!(effect instanceof PostShader)) return;
        for (Instance child : effect.children()) {
            switch (child) {
                case NumberValue n -> shader.setFloat(child.name(), (float) n.value);
                case BoolValue b -> shader.setInt(child.name(), b.value ? 1 : 0);
                case Vector3Value v -> shader.setVec3(child.name(), (float) v.value.x(), (float) v.value.y(), (float) v.value.z());
                default -> {}
            }
        }
    }
}
