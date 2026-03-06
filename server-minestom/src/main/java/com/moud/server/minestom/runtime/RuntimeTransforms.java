package com.moud.server.minestom.runtime;

import com.moud.core.math.Quat;
import com.moud.core.math.Transform;
import com.moud.core.math.Vec3;
import com.moud.core.scene.Node;
import com.moud.core.util.ParseUtils;

final class RuntimeTransforms {
    private RuntimeTransforms() {
    }

    static Transform toLocal(Transform world, Transform parentWorld) {
        if (world == null) {
            return Transform.IDENTITY;
        }
        if (parentWorld == null) {
            return world;
        }
        Quat invRot = parentWorld.rot().inverse();
        Vec3 delta = new Vec3(
                world.pos().x() - parentWorld.pos().x(),
                world.pos().y() - parentWorld.pos().y(),
                world.pos().z() - parentWorld.pos().z()
        );
        Vec3 deltaRot = invRot.rotate(delta);

        double px = parentWorld.scale().x() == 0.0 ? deltaRot.x() : deltaRot.x() / parentWorld.scale().x();
        double py = parentWorld.scale().y() == 0.0 ? deltaRot.y() : deltaRot.y() / parentWorld.scale().y();
        double pz = parentWorld.scale().z() == 0.0 ? deltaRot.z() : deltaRot.z() / parentWorld.scale().z();
        Vec3 posLocal = new Vec3(px, py, pz);

        Quat rotLocal = invRot.mul(world.rot()).normalized();
        Vec3 scaleLocal = new Vec3(
                parentWorld.scale().x() == 0.0 ? world.scale().x() : world.scale().x() / parentWorld.scale().x(),
                parentWorld.scale().y() == 0.0 ? world.scale().y() : world.scale().y() / parentWorld.scale().y(),
                parentWorld.scale().z() == 0.0 ? world.scale().z() : world.scale().z() / parentWorld.scale().z()
        );
        return new Transform(posLocal, rotLocal, scaleLocal);
    }

    static Transform worldTransform(Node node) {
        if (node == null) {
            return Transform.IDENTITY;
        }
        boolean inherit = shouldInheritTransform(node);
        Transform parent = inherit ? worldTransform(node.parent()) : Transform.IDENTITY;
        Transform local = localTransform(node);
        return parent.compose(local);
    }

    private static boolean shouldInheritTransform(Node node) {
        if (node == null) {
            return true;
        }
        String v = node.getProperty("@inherit_transform");
        if (v == null || v.isBlank()) {
            return true;
        }
        String s = v.trim().toLowerCase();
        return !("false".equals(s) || "0".equals(s));
    }

    private static Transform localTransform(Node node) {
        if (node == null) {
            return Transform.IDENTITY;
        }

        double x = ParseUtils.parseFloat(node.getProperty("x"), 0.0f);
        double y = ParseUtils.parseFloat(node.getProperty("y"), 0.0f);
        double z = ParseUtils.parseFloat(node.getProperty("z"), 0.0f);

        float rx = ParseUtils.parseFloat(node.getProperty("rx"), 0.0f);
        float ry = ParseUtils.parseFloat(node.getProperty("ry"), 0.0f);
        float rz = ParseUtils.parseFloat(node.getProperty("rz"), 0.0f);
        Quat rot = Quat.fromEulerDeg(rx, ry, rz);

        boolean hasSize = node.getProperty("sx") != null || node.getProperty("sy") != null || node.getProperty("sz") != null;
        double sx = hasSize ? Math.max(1e-6, ParseUtils.parseFloat(node.getProperty("sx"), 1.0f)) : 1.0;
        double sy = hasSize ? Math.max(1e-6, ParseUtils.parseFloat(node.getProperty("sy"), 1.0f)) : 1.0;
        double sz = hasSize ? Math.max(1e-6, ParseUtils.parseFloat(node.getProperty("sz"), 1.0f)) : 1.0;
        Vec3 scale = new Vec3(sx, sy, sz);

        Vec3 pivot = hasSize
                ? new Vec3(x + sx * 0.5, y + sy * 0.5, z + sz * 0.5)
                : new Vec3(x, y, z);

        return new Transform(pivot, rot, scale);
    }
}

