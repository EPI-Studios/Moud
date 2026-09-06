package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.instanced.BuiltinShader;
import com.meekdev.amnetic.client.instanced.InstanceBatch;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.Transforms;
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

    private static final Identifier ID = Identifier.fromNamespaceAndPath("moud", "parts");

    // batch.add packs straight into its buffer and keeps no reference, so these are reused
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final Quaternionf ROTATION = new Quaternionf();
    private static final Vector4f TINT = new Vector4f();

    private Parts() {}

    public static void register() {
        InstancedMesh.builder(BuiltinShader.TRANSFORM_COLOR)
                .geometry(MeshData.unitCube())
                .phase(InstancePhase.WORLD_LAST)
                .writeGBuffer(true)
                .castsShadow()
                .onRender(Parts::write)
                .register(ID);
    }

    private static void write(InstanceRenderContext ctx, InstanceBatch<BuiltinShader.TransformColor> batch) {
        List<Part> parts = ClientScene.tree().ofClass(Classes.PART);
        var cam = ctx.cameraPos();

        for (int n = 0; n < parts.size(); n++) {
            Part p = parts.get(n);
            if (!p.visible || p.transparency >= 1.0) continue;

            CFrame world = Transforms.world(p);
            Vec3 pos = world.position();
            Quat rot = world.rotation();
            Vec3 size = p.size;

            ROTATION.set((float) rot.x(), (float) rot.y(), (float) rot.z(), (float) rot.w());
            // vertices are camera relative, the way minecraft draws the world
            MATRIX.translation(
                            (float) (pos.x() - cam.x),
                            (float) (pos.y() - cam.y),
                            (float) (pos.z() - cam.z))
                    .rotate(ROTATION)
                    .scale((float) size.x(), (float) size.y(), (float) size.z());

            Color c = p.color;
            TINT.set(c.r(), c.g(), c.b(), (float) (1.0 - p.transparency));

            float radius = (float) (size.length() * 0.5);
            batch.addVisible(new BuiltinShader.TransformColor(MATRIX, TINT), pos.x(), pos.y(), pos.z(), radius);
        }
    }
}
