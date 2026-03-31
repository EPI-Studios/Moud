package com.moud.client.fabric.scene;

import com.moud.net.protocol.SceneSnapshot;
import java.util.HashMap;
import java.util.List;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SceneNodeTransforms {
    private static final float SCALE_EPS = 1.0e-4f;

    private SceneNodeTransforms() {
    }

    public static boolean tryWorldPosition(long nodeId, Vector3f out) {
        if (out == null || nodeId <= 0L) {
            return false;
        }
        Pose pose = worldPose(nodeId, new HashMap<>());
        out.set(pose.pos);
        return true;
    }

    private static Pose worldPose(long nodeId, HashMap<Long, Pose> cache) {
        if (nodeId <= 0L) {
            return Pose.IDENTITY;
        }
        Pose cached = cache.get(nodeId);
        if (cached != null) {
            return cached;
        }

        SceneSnapshot.NodeSnapshot node = ClientSceneBus.getNode(nodeId);
        if (node == null) {
            return Pose.IDENTITY;
        }

        Pose local = localPose(node);
        Pose world = new Pose();
        if (local.inherit && node.parentId() > 0L) {
            Pose parent = worldPose(node.parentId(), cache);
            Pose.compose(parent, local, world);
        } else {
            Pose.copy(local, world);
        }
        cache.put(nodeId, world);
        return world;
    }

    private static Pose localPose(SceneSnapshot.NodeSnapshot node) {
        Pose pose = new Pose();
        float x = 0.0f;
        float y = 0.0f;
        float z = 0.0f;
        float rx = 0.0f;
        float ry = 0.0f;
        float rz = 0.0f;
        float sx = 1.0f;
        float sy = 1.0f;
        float sz = 1.0f;
        String inheritRaw = null;

        List<SceneSnapshot.Property> props = node.properties();
        if (props != null) {
            for (SceneSnapshot.Property prop : props) {
                if (prop == null || prop.key() == null) {
                    continue;
                }
                String key = prop.key();
                String value = prop.value();
                switch (key) {
                    case "x" -> x = parseFloat(value, x);
                    case "y" -> y = parseFloat(value, y);
                    case "z" -> z = parseFloat(value, z);
                    case "rx" -> rx = parseFloat(value, rx);
                    case "ry" -> ry = parseFloat(value, ry);
                    case "rz" -> rz = parseFloat(value, rz);
                    case "sx" -> sx = Math.max(SCALE_EPS, parseFloat(value, sx));
                    case "sy" -> sy = Math.max(SCALE_EPS, parseFloat(value, sy));
                    case "sz" -> sz = Math.max(SCALE_EPS, parseFloat(value, sz));
                    case "@inherit_transform" -> inheritRaw = value;
                    default -> {
                    }
                }
            }
        }

        pose.pos.set(x, y, z);
        pose.rot.set(quatFromEulerDeg(rx, ry, rz));
        pose.scale.set(sx, sy, sz);
        pose.inherit = parseBool(inheritRaw, true);
        return pose;
    }

    private static Quaternionf quatFromEulerDeg(float rxDeg, float ryDeg, float rzDeg) {
        return new Quaternionf().rotationXYZ(
                (float) Math.toRadians(Float.isFinite(rxDeg) ? rxDeg : 0.0f),
                (float) Math.toRadians(Float.isFinite(ryDeg) ? ryDeg : 0.0f),
                (float) Math.toRadians(Float.isFinite(rzDeg) ? rzDeg : 0.0f)
        );
    }

    private static float parseFloat(String value, float fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean parseBool(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String s = value.trim().toLowerCase();
        return switch (s) {
            case "true", "1", "t", "yes", "y" -> true;
            case "false", "0", "f", "no", "n" -> false;
            default -> fallback;
        };
    }

    private static final class Pose {
        private static final Pose IDENTITY = new Pose(true);

        private final Vector3f pos = new Vector3f();
        private final Quaternionf rot = new Quaternionf();
        private final Vector3f scale = new Vector3f(1, 1, 1);
        private boolean inherit = true;

        private Pose() {
        }

        private Pose(boolean identity) {
            if (identity) {
                pos.set(0, 0, 0);
                rot.identity();
                scale.set(1, 1, 1);
                inherit = true;
            }
        }

        private static void copy(Pose src, Pose dst) {
            dst.pos.set(src.pos);
            dst.rot.set(src.rot);
            dst.scale.set(src.scale);
            dst.inherit = src.inherit;
        }

        private static void compose(Pose parent, Pose child, Pose out) {
            out.pos.set(child.pos).mul(parent.scale);
            parent.rot.transform(out.pos);
            out.pos.add(parent.pos);
            out.rot.set(parent.rot).mul(child.rot).normalize();
            out.scale.set(parent.scale).mul(child.scale);
            out.inherit = child.inherit;
        }
    }
}
