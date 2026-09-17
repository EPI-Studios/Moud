package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.instanced.InstanceBatch;
import com.meekdev.amnetic.client.instanced.InstanceLayout;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.RenderState;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.interp.Motion;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.MeshPart;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.part.PartShape;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.mod.client.ClientScene;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector4f;

public final class Parts {

    private static final Identifier[] STILL = ids("parts_still");
    private static final Identifier[] MOVING = ids("parts_moving");
    private static final Identifier[] GLASS = ids("parts_glass");

    private static final Matrix4f MATRIX = new Matrix4f();
    private static final Quaternionf ROTATION = new Quaternionf();
    private static final Vector4f TINT = new Vector4f();

    private static final int[] STILL_COUNTS = new int[PartShape.values().length];
    private static final int[] MOVING_COUNTS = new int[PartShape.values().length];

    public static int stillCount() {
        return total(STILL_COUNTS);
    }

    public static int movingCount() {
        return total(MOVING_COUNTS);
    }

    private static int total(int[] counts) {
        int sum = 0;
        for (int count : counts) sum += count;
        return sum;
    }

    private static Identifier[] ids(String base) {
        PartShape[] shapes = PartShape.values();
        Identifier[] out = new Identifier[shapes.length];
        for (int at = 0; at < shapes.length; at++) {
            String path = shapes[at] == PartShape.BLOCK ? base : base + "_" + shapes[at].name().toLowerCase(Locale.ROOT);
            out[at] = Identifier.fromNamespaceAndPath("moud", path);
        }
        return out;
    }

    private Parts() {}

    public static void register() {
        for (PartShape shape : PartShape.values()) {
            int at = shape.ordinal();
            mesh(shape).staticInstances().onRender((ctx, batch) -> renderStatic(shape, ctx, batch)).register(STILL[at]);
            mesh(shape).onRender((ctx, batch) -> renderMoving(shape, ctx, batch)).register(MOVING[at]);
            glass(shape).onRender((ctx, batch) -> renderTransparent(shape, ctx, batch)).register(GLASS[at]);
        }
    }

    private static final InstanceLayout LAYOUT =
            InstanceLayout.builder().mat4(1).vec4(5).vec2(6).build();

    private record Lit(Matrix4f transform, Vector4f color, Vector2f light) {}

    public static int lightMap() {
        GpuTextureView view = Minecraft.getInstance().gameRenderer.levelLightmap();
        return view != null && view.texture() instanceof GlTexture texture ? texture.glId() : 0;
    }

    public static void invalidateStatic() {
        for (Identifier id : STILL) InstancedMesh.invalidate(id);
    }

    private static InstancedMesh.Builder<Lit> glass(PartShape shape) {
        return InstancedMesh.<Lit>builder(LAYOUT,
                        (inst, p) -> p.putMat4(inst.transform()).putVec4(inst.color())
                                .putVec2(inst.light().x, inst.light().y))
                .shader(Identifier.fromNamespaceAndPath("moud", "instance/part"))
                .extraSampler("LightMap", Parts::lightMap, 1)
                .geometry(ShapeMeshes.of(shape))
                .renderState(RenderState.builder()
                        .blend(RenderState.BlendMode.ALPHA)
                        .depthWrite(false)
                        .backfaceCulling(false)
                        .build())
                .phase(InstancePhase.WORLD_TRANSLUCENT)
                .writeGBuffer(false)
                .worldSpace();
    }

    private static InstancedMesh.Builder<Lit> mesh(PartShape shape) {
        return InstancedMesh.<Lit>builder(LAYOUT,
                        (inst, p) -> p.putMat4(inst.transform()).putVec4(inst.color())
                                .putVec2(inst.light().x, inst.light().y))
                .shader(Identifier.fromNamespaceAndPath("moud", "instance/part"))
                .extraSampler("LightMap", Parts::lightMap, 1)
                .geometry(ShapeMeshes.of(shape))
                .renderState(RenderState.DEFAULT)
                .phase(InstancePhase.WORLD_LAST)
                .writeGBuffer(true)
                .worldSpace()
                .castsShadow();
    }

    private static void renderStatic(PartShape shape, InstanceRenderContext ctx, InstanceBatch<Lit> batch) {
        InstanceTree tree = ClientScene.tree();
        if (tree == null) return;
        Motion motion = ClientScene.motion();
        List<Part> parts = tree.ofClass(Classes.PART);
        int emitted = 0;
        for (int n = 0; n < parts.size(); n++) {
            Part part = parts.get(n);
            if (!motion.isMoving(part) && write(shape, ctx, batch, motion, part, false, false)) emitted++;
        }
        STILL_COUNTS[shape.ordinal()] = emitted;
    }

    private static final List<Part> SEE_THROUGH = new ArrayList<>();

    private static void renderTransparent(PartShape shape, InstanceRenderContext ctx, InstanceBatch<Lit> batch) {
        InstanceTree tree = ClientScene.tree();
        if (tree == null) return;
        Motion motion = ClientScene.motion();
        var eye = ctx.cameraPos();
        SEE_THROUGH.clear();
        for (Part part : tree.ofClass(Classes.PART)) {
            if (part.shape == shape && isTransparent(part) && !ViewportFrame.inside(part)) SEE_THROUGH.add(part);
        }
        SEE_THROUGH.sort(Comparator.comparingDouble((Part part) -> {
            Vector3 at = motion.sample(part, ctx.deltaTick()).position();
            double dx = at.x() - eye.x, dy = at.y() - eye.y, dz = at.z() - eye.z;
            return dx * dx + dy * dy + dz * dz;
        }).reversed());
        for (Part part : SEE_THROUGH) write(shape, ctx, batch, motion, part, true, true);
    }

    private static boolean isTransparent(Part part) {
        return part.transparency > 0.001 && part.transparency < 1.0 || EditorView.ghost(part);
    }

    private static void renderMoving(PartShape shape, InstanceRenderContext ctx, InstanceBatch<Lit> batch) {
        Motion motion = ClientScene.motion();
        int emitted = 0;
        for (Instance instance : motion.moving()) {
            if (instance instanceof Part part && write(shape, ctx, batch, motion, part, true, false)) emitted++;
        }
        MOVING_COUNTS[shape.ordinal()] = emitted;
    }

    private static boolean write(PartShape shape, InstanceRenderContext ctx, InstanceBatch<Lit> batch,
            Motion motion, Part part, boolean cull, boolean glass) {
        if (part.shape != shape) return false;
        boolean ghost = EditorView.ghost(part);
        if (!ghost && (!part.visible || part.transparency >= 1.0)) return false;
        if (isTransparent(part) != glass) return false;
        if (part instanceof MeshPart) return false;
        if (Skins.wearsSkin(part)) return false;
        if (ViewportFrame.inside(part)) return false;

        CFrame world = motion.sample(part, ctx.deltaTick());
        Vector3 pos = world.position();
        Quat rot = world.rotation();
        Vector3 size = ShapeMeshes.scale(part);

        ROTATION.set((float) rot.x(), (float) rot.y(), (float) rot.z(), (float) rot.w());
        MATRIX.translation((float) pos.x(), (float) pos.y(), (float) pos.z())
                .rotate(ROTATION)
                .scale((float) size.x(), (float) size.y(), (float) size.z());

        Color c = part.color;
        if (ghost) TINT.set(0.55f + c.r() * 0.3f, 0.75f + c.g() * 0.2f, 1.0f, EditorView.GHOST_ALPHA);
        else TINT.set(c.r(), c.g(), c.b(), (float) (1.0 - part.transparency));

        float radius = (float) (size.length() * 0.5);
        Lit instance = new Lit(MATRIX, TINT, PartLight.of(part, pos));
        if (cull) {
            batch.addVisible(instance, pos.x(), pos.y(), pos.z(), radius);
        } else {
            batch.add(instance, pos.x(), pos.y(), pos.z(), radius);
        }
        return true;
    }
}
