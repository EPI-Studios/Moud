package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.ModelLighting;
import com.meekdev.amnetic.client.model.internal.OffscreenModelRenderer;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.moud.core.character.Limb;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.MeshPart;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.render.post.ScreenEffect;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.gl.Programs;
import com.meekdev.moud.mod.adapter.gl.RenderState;
import com.meekdev.moud.mod.adapter.gl.VertexArray;
import com.meekdev.moud.mod.client.ClientScene;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class Viewports {

    private static final float NEAR = 0.05f;
    private static final float FAR = 2000f;
    private static final int LARGEST = 4096;
    private static final Identifier VERTEX = Identifier.fromNamespaceAndPath("moud", "shaders/viewport/part.vsh");
    private static final Identifier FRAGMENT = Identifier.fromNamespaceAndPath("moud", "shaders/viewport/part.fsh");

    private static final class State {
        Framebuffer target;
        int width;
        int height;
        long drawnAt;
        boolean drawn;
        boolean seen;
    }

    private static final Map<ViewportFrame, State> STATES = new HashMap<>();
    private static final OffscreenModelRenderer MODELS = new OffscreenModelRenderer();
    private static ShaderProgram program;
    private static VertexArray cube;
    private static boolean failed;

    private Viewports() {}

    public static void register() {
        Pipeline.add(RenderStage.SETUP, 5, "moud viewports", ctx -> EditorView.filled(Viewports::drawAll));
    }

    public static void measure(ViewportFrame viewport, float width, float height) {
        State state = STATES.computeIfAbsent(viewport, key -> new State());
        double scale = viewport.resolutionScale;
        state.width = Math.clamp(Math.round(width * scale), 0, LARGEST);
        state.height = Math.clamp(Math.round(height * scale), 0, LARGEST);
        state.seen = true;
        viewport.measured(width, height);
    }

    public static int texture(ViewportFrame viewport) {
        State state = STATES.get(viewport);
        return state == null || !state.drawn || state.target == null ? 0 : state.target.colorTextureGlId(0);
    }

    private static void drawAll() {
        InstanceTree tree = ClientScene.tree();
        Iterator<Map.Entry<ViewportFrame, State>> each = STATES.entrySet().iterator();
        while (each.hasNext()) {
            Map.Entry<ViewportFrame, State> entry = each.next();
            if (tree == null || !entry.getKey().isAlive() || entry.getKey().tree() != tree || !entry.getValue().seen) {
                if (entry.getValue().target != null) entry.getValue().target.dispose();
                each.remove();
            } else {
                entry.getValue().seen = false;
            }
        }
        if (tree == null || failed) return;
        long now = System.nanoTime();
        for (Map.Entry<ViewportFrame, State> entry : STATES.entrySet()) {
            ViewportFrame viewport = entry.getKey();
            State state = entry.getValue();
            if (state.width < 1 || state.height < 1) continue;
            if (state.drawn && viewport.updateRate > 0 && now - state.drawnAt < 1.0e9 / viewport.updateRate) continue;
            try {
                draw(viewport, state);
                state.drawnAt = now;
                state.drawn = true;
            } catch (RuntimeException e) {
                failed = true;
                MoudMod.LOG.error("viewports can not be drawn, turning them off", e);
            } finally {
                GlState.endFullscreen();
            }
        }
    }

    private static void draw(ViewportFrame viewport, State state) {
        if (state.target == null || state.target.width() != state.width || state.target.height() != state.height) {
            if (state.target != null) state.target.dispose();
            state.target = Framebuffers.fixed(state.width, state.height, FramebufferSpec.builder().color(ColorFormat.RGBA8).depthTexture().build());
        }
        CFrame view = viewport.view();
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(viewport.fov()), (float) state.width / state.height,
                NEAR, FAR, RenderSystem.getDevice().isZZeroToOne());
        Matrix4f turn = rotation(view.rotation());
        scene(state.target, viewport, view, projection, viewport.parts());

        List<InterfaceEffects.Scoped> effects = new ArrayList<>();
        for (Instance child : viewport.children()) {
            if (child instanceof ScreenEffect effect && effect.enabled && effect.intensity > 0) {
                effects.add(new InterfaceEffects.Scoped(effect, 0, 0, state.width, state.height));
            }
        }
        if (effects.isEmpty()) return;
        effects.sort(Comparator.comparingDouble(scoped -> scoped.effect().order));
        Vector3 eye = view.position();
        Matrix4f relative = new Matrix4f(projection).mul(new Matrix4f(turn).invert());
        InterfaceEffects.apply(state.target, state.target.depthTextureGlId(),
                new InterfaceEffects.Eye(eye.x(), eye.y(), eye.z(), relative, new Matrix4f(relative).invert(), RenderSystem.getDevice().isZZeroToOne()),
                effects);
    }

    public static void view(Framebuffer target, CFrame view, double fov, List<Part> parts) {
        if (failed) return;
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(fov), (float) target.width() / target.height(),
                NEAR, FAR, RenderSystem.getDevice().isZZeroToOne());
        try {
            scene(target, LIGHTING, view, projection, parts);
        } finally {
            GlState.endFullscreen();
        }
    }

    private static final ViewportFrame LIGHTING = new ViewportFrame();

    private static void scene(Framebuffer target, ViewportFrame viewport, CFrame view, Matrix4f projection, List<Part> parts) {
        Matrix4f turn = rotation(view.rotation());
        Matrix4f camera = new Matrix4f().translation((float) view.position().x(), (float) view.position().y(), (float) view.position().z()).mul(turn);
        Matrix4f projView = new Matrix4f(projection).mul(new Matrix4f(camera).invert());

        List<Part> solid = new ArrayList<>();
        List<Part> glass = new ArrayList<>();
        List<MeshPart> meshes = new ArrayList<>();
        for (Part part : parts) {
            if (!part.visible || part.transparency >= 1 || part instanceof Limb) continue;
            if (part instanceof MeshPart mesh) {
                if (!mesh.meshId.isEmpty()) meshes.add(mesh);
            } else if (part.transparency > 0) {
                glass.add(part);
            } else {
                solid.add(part);
            }
        }
        Vector3 eye = view.position();
        glass.sort(Comparator.comparingDouble((Part part) -> Transforms.world(part).position().distance(eye)).reversed());

        target.begin();
        target.clear(0, 0, 0, 0);
        RenderState.depthTest(true);
        RenderState.depthLessOrEqual();
        RenderState.depthMask(true);
        RenderState.cull(false);
        RenderState.blend(false);
        cubes(viewport, projView, eye, solid);
        models(viewport, projView, meshes);
        if (!glass.isEmpty()) {
            RenderState.blend(true);
            RenderState.alphaBlend();
            RenderState.depthMask(false);
            cubes(viewport, projView, eye, glass);
            RenderState.depthMask(true);
            RenderState.blend(false);
        }
        target.end();
    }

    private static void cubes(ViewportFrame viewport, Matrix4f projView, Vector3 eye, List<Part> parts) {
        if (parts.isEmpty()) return;
        if (cube == null) cube = VertexArray.of(MeshData.unitCube());
        if (program == null) program = new ShaderProgram(VERTEX, FRAGMENT);
        program.begin();
        program.setMatrix4("ViewProj", projView);
        program.setVec3("Ambient", viewport.ambient.r(), viewport.ambient.g(), viewport.ambient.b());
        program.setVec3("LightColor", viewport.lightColor.r(), viewport.lightColor.g(), viewport.lightColor.b());
        program.setVec3("LightDirection", (float) viewport.lightDirection.x(), (float) viewport.lightDirection.y(), (float) viewport.lightDirection.z());
        program.setVec3("CameraPosition", (float) eye.x(), (float) eye.y(), (float) eye.z());
        cube.bind();
        Matrix4f model = new Matrix4f();
        for (Part part : parts) {
            CFrame world = Transforms.world(part);
            model.translation((float) world.position().x(), (float) world.position().y(), (float) world.position().z())
                    .mul(rotation(world.rotation()))
                    .scale((float) part.size.x(), (float) part.size.y(), (float) part.size.z());
            Color color = part.color;
            program.setMatrix4("Model", model);
            program.setVec4("Tint", color.r(), color.g(), color.b(), (float) (1 - part.transparency));
            cube.draw();
        }
        VertexArray.unbind();
        Programs.use(0);
    }

    private static void models(ViewportFrame viewport, Matrix4f projView, List<MeshPart> meshes) {
        if (meshes.isEmpty()) return;
        ModelLighting lighting = ModelLighting.INSTANCE;
        float[] before = {lighting.sunX(), lighting.sunY(), lighting.sunZ(), lighting.sunR(), lighting.sunG(), lighting.sunB(),
                lighting.sunIntensity(), lighting.ambientStrength()};
        Vector3 toLight = viewport.lightDirection.neg().normalize();
        Color sun = viewport.lightColor;
        Color ambient = viewport.ambient;
        lighting.sunDirection((float) toLight.x(), (float) toLight.y(), (float) toLight.z())
                .sunColor(sun.r(), sun.g(), sun.b())
                .sunIntensity(1)
                .ambientStrength((ambient.r() + ambient.g() + ambient.b()) / 3f);
        float dt = 1f / 60f;
        try {
            for (MeshPart part : meshes) {
                Model model = Meshes.modelFor(part.meshId);
                if (model == null || !model.isReady()) continue;
                Matrix4f world = Meshes.placement(Transforms.world(part), part.size, model, new Matrix4f());
                MODELS.draw(model.internalGpu(), projView, world, Meshes.pose(part, model, dt), 15, 15, 1);
            }
        } finally {
            lighting.sunDirection(before[0], before[1], before[2]).sunColor(before[3], before[4], before[5])
                    .sunIntensity(before[6]).ambientStrength(before[7]);
        }
    }

    private static Matrix4f rotation(Quat quat) {
        return new Matrix4f().rotation(new Quaternionf((float) quat.x(), (float) quat.y(), (float) quat.z(), (float) quat.w()));
    }
}
