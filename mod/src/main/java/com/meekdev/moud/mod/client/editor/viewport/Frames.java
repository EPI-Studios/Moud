package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.mod.client.editor.document.Edit;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.document.SetProperty;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

final class Frames {

    private static final double SMALLEST_SIZE = 0.01;

    private Frames() {}

    static Matrix4f matrix(Instance instance, Vector3 camera) {
        CFrame world = Transforms.world(instance);
        Vector3 at = world.position().sub(camera);
        Quat rotation = world.rotation();
        Matrix4f matrix = new Matrix4f()
                .translate((float) at.x(), (float) at.y(), (float) at.z())
                .rotate(new Quaternionf((float) rotation.x(), (float) rotation.y(), (float) rotation.z(), (float) rotation.w()));
        if (instance instanceof Part part) matrix.scale((float) part.size.x(), (float) part.size.y(), (float) part.size.z());
        return matrix;
    }

    static List<Edit> writes(SceneDocument document, Instance instance, Matrix4f matrix, Vector3 camera, boolean resize) {
        Vector3f translation = matrix.getTranslation(new Vector3f());
        Quaternionf rotation = new Matrix4f(matrix).normalize3x3().getUnnormalizedRotation(new Quaternionf()).normalize();
        CFrame world = new CFrame(
                new Vector3(translation.x + camera.x(), translation.y + camera.y(), translation.z + camera.z()),
                new Quat(rotation.x, rotation.y, rotation.z, rotation.w));
        Spatial spatial = (Spatial) instance;
        CFrame local = Transforms.localFor(instance, world);
        if (!spatial.pivot.equals(Vector3.ZERO)) local = local.mul(CFrame.at(spatial.pivot));
        PropertyDef cframe = instance.def().property("cframe");
        if (!resize || !(instance instanceof Part)) return List.of(new SetProperty(document.ref(instance.id()), cframe.index(), local, "Transform"));
        Vector3f scale = matrix.getScale(new Vector3f());
        Vector3 size = new Vector3(Math.max(SMALLEST_SIZE, scale.x), Math.max(SMALLEST_SIZE, scale.y), Math.max(SMALLEST_SIZE, scale.z));
        PropertyDef sized = instance.def().property("size");
        return List.of(new SetProperty(document.ref(instance.id()), cframe.index(), local, "Transform"),
                new SetProperty(document.ref(instance.id()), sized.index(), size, "Transform"));
    }

    static Vector3[] corners(Part part) {
        CFrame world = Transforms.world(part);
        Vector3 half = part.size.mul(0.5);
        Vector3[] corners = new Vector3[8];
        for (int n = 0; n < 8; n++) {
            Vector3 local = new Vector3((n & 1) == 0 ? -half.x() : half.x(), (n & 2) == 0 ? -half.y() : half.y(), (n & 4) == 0 ? -half.z() : half.z());
            corners[n] = world.pointToWorld(local);
        }
        return corners;
    }

    static @Nullable Vector3 center(Instance instance) {
        return instance instanceof Spatial ? Transforms.world(instance).position() : null;
    }

    static double radius(Instance instance) {
        return instance instanceof Part part ? part.size.length() * 0.5 : 0.5;
    }
}
