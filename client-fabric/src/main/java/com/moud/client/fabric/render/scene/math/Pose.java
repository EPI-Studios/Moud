package com.moud.client.fabric.render.scene.math;

import com.moud.client.fabric.render.scene.util.NodePropertyUtils;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Pose {
    public static final Pose IDENTITY = new Pose(true);

    public final Vector3f pos = new Vector3f();
    public final Quaternionf rot = new Quaternionf();
    public final Vector3f scale = new Vector3f(1.0f, 1.0f, 1.0f);
    public boolean inherit = true;

    public Pose() {
    }

    public Pose(boolean identity) {
        if (identity) {
            pos.set(0.0f, 0.0f, 0.0f);
            rot.identity();
            scale.set(1.0f, 1.0f, 1.0f);
            inherit = true;
        }
    }

    public static void copy(Pose src, Pose dst) {
        if (src == null || dst == null) {
            return;
        }
        dst.pos.set(src.pos);
        dst.rot.set(src.rot);
        dst.scale.set(src.scale);
        dst.inherit = src.inherit;
    }

    public static void interpolate(Pose a, Pose b, float t, Pose out) {
        if (a == null || b == null || out == null) {
            return;
        }
        float alpha = NodePropertyUtils.clamp01(t);
        out.pos.set(a.pos).lerp(b.pos, alpha);
        out.rot.set(a.rot).slerp(b.rot, alpha).normalize();
        out.scale.set(a.scale).lerp(b.scale, alpha);
        out.inherit = b.inherit;
    }

    public static void compose(Pose parent, Pose child, Pose out) {
        if (child == null || out == null) {
            return;
        }
        if (parent == null) {
            copy(child, out);
            return;
        }
        out.pos.set(child.pos).mul(parent.scale);
        parent.rot.transform(out.pos);
        out.pos.add(parent.pos);
        out.rot.set(parent.rot).mul(child.rot).normalize();
        out.scale.set(parent.scale).mul(child.scale);
        out.inherit = child.inherit;
    }

    public static boolean approxEquals(Pose a, Pose b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.inherit != b.inherit) {
            return false;
        }
        float posEps = 1e-5f;
        if (Math.abs(a.pos.x - b.pos.x) > posEps
                || Math.abs(a.pos.y - b.pos.y) > posEps
                || Math.abs(a.pos.z - b.pos.z) > posEps) {
            return false;
        }
        float scaleEps = 1e-5f;
        if (Math.abs(a.scale.x - b.scale.x) > scaleEps
                || Math.abs(a.scale.y - b.scale.y) > scaleEps
                || Math.abs(a.scale.z - b.scale.z) > scaleEps) {
            return false;
        }
        float rotEps = 1e-4f;
        return Math.abs(a.rot.x - b.rot.x) <= rotEps
                && Math.abs(a.rot.y - b.rot.y) <= rotEps
                && Math.abs(a.rot.z - b.rot.z) <= rotEps
                && Math.abs(a.rot.w - b.rot.w) <= rotEps;
    }
}
