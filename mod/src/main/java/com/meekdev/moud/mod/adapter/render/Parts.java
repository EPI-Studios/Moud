package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.instanced.BuiltinShader;
import com.meekdev.amnetic.client.instanced.InstanceBatch;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.interp.Motion;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.mod.client.ClientScene;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

public final class Parts {

    // two batches, because 11.3 says a static place costs approximately nothing per frame.
    // still parts are emitted once and reused; only what moves is repacked every frame
    private static final Identifier STILL = Identifier.fromNamespaceAndPath("moud", "parts_still");
    private static final Identifier MOVING = Identifier.fromNamespaceAndPath("moud", "parts_moving");

    // batch.add packs straight into its buffer and keeps no reference, so these are reused
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final Quaternionf ROTATION = new Quaternionf();
    private static final Vector4f TINT = new Vector4f();

    private Parts() {}

    public static void register() {
        // the still batch is packed once and reused for every later frame, so it must not be culled
        // against the frustum it happened to be packed in. gpu culling reads the same bounds per
        // frame instead, which is the only kind that stays true as the camera turns
        mesh(STILL).staticInstances().gpuCull().onRender(Parts::still).register(STILL);
        mesh(MOVING).onRender(Parts::moving).register(MOVING);
    }

    // called once the set of still parts changes, never per frame
    public static void invalidateStill() {
        InstancedMesh.invalidate(STILL);
    }

    private static InstancedMesh.Builder<BuiltinShader.TransformColor> mesh(Identifier id) {
        return InstancedMesh.builder(BuiltinShader.TRANSFORM_COLOR)
                .geometry(MeshData.unitCube())
                .flatShaded()
                .phase(InstancePhase.WORLD_LAST)
                .writeGBuffer(true)
                // absolute positions, because the still batch is uploaded once and a camera
                // relative one would be frozen at the camera the frame it was packed
                .worldSpace()
                .castsShadow();
    }

    private static void still(InstanceRenderContext ctx, InstanceBatch<BuiltinShader.TransformColor> batch) {
        InstanceTree tree = ClientScene.tree();
        if (tree == null) return;
        Motion motion = ClientScene.motion();
        List<Part> parts = tree.ofClass(Classes.PART);
        for (int n = 0; n < parts.size(); n++) {
            Part part = parts.get(n);
            if (!motion.isMoving(part)) write(ctx, batch, motion, part, false);
        }
    }

    private static void moving(InstanceRenderContext ctx, InstanceBatch<BuiltinShader.TransformColor> batch) {
        Motion motion = ClientScene.motion();
        for (Instance instance : motion.moving()) {
            if (instance instanceof Part part) write(ctx, batch, motion, part, true);
        }
    }

    private static void write(InstanceRenderContext ctx, InstanceBatch<BuiltinShader.TransformColor> batch,
            Motion motion, Part part, boolean cull) {
        if (!part.visible || part.transparency >= 1.0) return;

        CFrame world = motion.sample(part);
        Vec3 pos = world.position();
        Quat rot = world.rotation();
        Vec3 size = part.size;

        ROTATION.set((float) rot.x(), (float) rot.y(), (float) rot.z(), (float) rot.w());
        MATRIX.translation((float) pos.x(), (float) pos.y(), (float) pos.z())
                .rotate(ROTATION)
                .scale((float) size.x(), (float) size.y(), (float) size.z());

        Color c = part.color;
        TINT.set(c.r(), c.g(), c.b(), (float) (1.0 - part.transparency));

        float radius = (float) (size.length() * 0.5);
        BuiltinShader.TransformColor instance = new BuiltinShader.TransformColor(MATRIX, TINT);
        if (cull) {
            batch.addVisible(instance, pos.x(), pos.y(), pos.z(), radius);
        } else {
            batch.add(instance, pos.x(), pos.y(), pos.z(), radius);
        }
    }
}
