package com.moud.server.minestom.collision;

import com.moud.core.NodeTypeRegistry;
import com.moud.core.scene.Node;

import java.util.ArrayDeque;

public final class SceneNodeTransform {
    private SceneNodeTransform() {
    }

    public static boolean shouldInheritTransform(Node node) {
        if (node == null) {
            return true;
        }
        String v = node.getProperty("@inherit_transform");
        if (v == null || v.isBlank()) {
            return true;
        }
        String s = v.trim().toLowerCase();
        return !"false".equals(s) && !"0".equals(s);
    }

    public static Transform localTransform(Node node, String typeId) {
        if (node == null) {
            return Transform.IDENTITY;
        }
        double x = parseDouble(node.getProperty("x"), 0.0);
        double y = parseDouble(node.getProperty("y"), 0.0);
        double z = parseDouble(node.getProperty("z"), 0.0);
        Quat rotation = Quat.fromEulerDeg(
                parseDouble(node.getProperty("rx"), 0.0),
                parseDouble(node.getProperty("ry"), 0.0),
                parseDouble(node.getProperty("rz"), 0.0)
        );

        boolean hasScale = node.getProperty("sx") != null
                || node.getProperty("sy") != null
                || node.getProperty("sz") != null;
        double sx = hasScale ? safeScale(parseDouble(node.getProperty("sx"), 1.0)) : 1.0;
        double sy = hasScale ? safeScale(parseDouble(node.getProperty("sy"), 1.0)) : 1.0;
        double sz = hasScale ? safeScale(parseDouble(node.getProperty("sz"), 1.0)) : 1.0;

        boolean pivotIsMinCorner = "CSGBox".equals(typeId) || "CSGBlock".equals(typeId);
        double px = pivotIsMinCorner && hasScale ? x + sx * 0.5 : x;
        double py = pivotIsMinCorner && hasScale ? y + sy * 0.5 : y;
        double pz = pivotIsMinCorner && hasScale ? z + sz * 0.5 : z;
        return new Transform(new Vec3(px, py, pz), rotation, new Vec3(sx, sy, sz));
    }

    public static Transform worldTransformOf(Node node, NodeTypeRegistry types) {
        if (node == null) {
            return Transform.IDENTITY;
        }
        Transform world = Transform.IDENTITY;
        ArrayDeque<Node> chain = new ArrayDeque<>();
        Node walker = node;
        while (walker != null) {
            chain.push(walker);
            walker = walker.parent();
        }
        for (Node n : chain) {
            if (n.parent() == null) {
                continue;
            }
            String typeId = types == null ? null : types.typeIdFor(n);
            Transform local = localTransform(n, typeId);
            if (shouldInheritTransform(n)) {
                world = world.compose(local);
            } else {
                world = local;
            }
        }
        return world;
    }

    public static double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static double safeScale(double value) {
        if (!Double.isFinite(value)) {
            return 1.0;
        }
        return Math.abs(value) < 1e-6 ? 1e-6 : value;
    }

    public record Transform(Vec3 pos, Quat rot, Vec3 scale) {
        public static final Transform IDENTITY = new Transform(new Vec3(0.0, 0.0, 0.0), Quat.IDENTITY, new Vec3(1.0, 1.0, 1.0));

        public Transform compose(Transform child) {
            Vec3 scaled = new Vec3(child.pos.x * scale.x, child.pos.y * scale.y, child.pos.z * scale.z);
            Vec3 worldPos = pos.add(rot.rotate(scaled));
            return new Transform(worldPos, rot.mul(child.rot).normalized(), scale.mul(child.scale));
        }

        public Vec3 apply(double x, double y, double z) {
            Vec3 scaled = new Vec3(x * scale.x, y * scale.y, z * scale.z);
            return pos.add(rot.rotate(scaled));
        }

        public Transform inverse() {
            Quat invRot = rot.conjugate();
            double invSx = 1.0 / safeScale(scale.x);
            double invSy = 1.0 / safeScale(scale.y);
            double invSz = 1.0 / safeScale(scale.z);
            Vec3 invScale = new Vec3(invSx, invSy, invSz);
            Vec3 negP = new Vec3(-pos.x, -pos.y, -pos.z);
            Vec3 rotated = invRot.rotate(negP);
            Vec3 invPos = new Vec3(rotated.x * invSx, rotated.y * invSy, rotated.z * invSz);
            return new Transform(invPos, invRot, invScale);
        }

        public static Transform ofPivoted(Vec3 position, Vec3 pivot, Quat rotation, Vec3 scale) {
            // T(pivot) * R * T(-pivot) * T(position) * S(scale)
            // = T(pivot + rot * (position - pivot)) * R * S(scale)
            Vec3 pos = pivot.add(rotation.rotate(position.sub(pivot)));
            return new Transform(pos, rotation, scale);
        }
    }

    public record Vec3(double x, double y, double z) {
        public Vec3 add(Vec3 other) {
            return new Vec3(x + other.x, y + other.y, z + other.z);
        }

        public Vec3 sub(Vec3 other) {
            return new Vec3(x - other.x, y - other.y, z - other.z);
        }

        public Vec3 mul(Vec3 other) {
            return new Vec3(x * other.x, y * other.y, z * other.z);
        }
    }

    public record Quat(double x, double y, double z, double w) {
        public static final Quat IDENTITY = new Quat(0.0, 0.0, 0.0, 1.0);

        public static Quat fromEulerDeg(double rxDeg, double ryDeg, double rzDeg) {
            double hx = Math.toRadians(rxDeg) * 0.5;
            double hy = Math.toRadians(ryDeg) * 0.5;
            double hz = Math.toRadians(rzDeg) * 0.5;
            return new Quat(Math.sin(hx), 0.0, 0.0, Math.cos(hx))
                    .mul(new Quat(0.0, Math.sin(hy), 0.0, Math.cos(hy)))
                    .mul(new Quat(0.0, 0.0, Math.sin(hz), Math.cos(hz)))
                    .normalized();
        }

        public static Quat fromEulerDegZYX(double rxDeg, double ryDeg, double rzDeg) {
            double hx = Math.toRadians(rxDeg) * 0.5;
            double hy = Math.toRadians(ryDeg) * 0.5;
            double hz = Math.toRadians(rzDeg) * 0.5;
            // Rz * Ry * Rx
            return new Quat(0.0, 0.0, Math.sin(hz), Math.cos(hz))
                    .mul(new Quat(0.0, Math.sin(hy), 0.0, Math.cos(hy)))
                    .mul(new Quat(Math.sin(hx), 0.0, 0.0, Math.cos(hx)))
                    .normalized();
        }

        public Quat mul(Quat other) {
            return new Quat(
                    w * other.x + x * other.w + y * other.z - z * other.y,
                    w * other.y - x * other.z + y * other.w + z * other.x,
                    w * other.z + x * other.y - y * other.x + z * other.w,
                    w * other.w - x * other.x - y * other.y - z * other.z
            );
        }

        public Quat conjugate() {
            return new Quat(-x, -y, -z, w);
        }

        public Vec3 toEulerDegXYZ() {
            double m02 = 2.0 * (x * z + w * y);
            if (m02 > 0.99999) {
                double rx = Math.atan2(2.0 * (y * z + w * x), 1.0 - 2.0 * (x * x + z * z));
                return new Vec3(Math.toDegrees(rx), 90.0, 0.0);
            }
            if (m02 < -0.99999) {
                double rx = Math.atan2(2.0 * (y * z + w * x), 1.0 - 2.0 * (x * x + z * z));
                return new Vec3(Math.toDegrees(rx), -90.0, 0.0);
            }
            double rx = Math.atan2(-2.0 * (y * z - w * x), 1.0 - 2.0 * (x * x + y * y));
            double ry = Math.asin(m02);
            double rz = Math.atan2(-2.0 * (x * y - w * z), 1.0 - 2.0 * (y * y + z * z));
            return new Vec3(Math.toDegrees(rx), Math.toDegrees(ry), Math.toDegrees(rz));
        }

        public Quat normalized() {
            double n = Math.sqrt(x * x + y * y + z * z + w * w);
            if (n <= 0.0) {
                return IDENTITY;
            }
            double inv = 1.0 / n;
            return new Quat(x * inv, y * inv, z * inv, w * inv);
        }

        public Vec3 rotate(Vec3 v) {
            double tx = 2.0 * (y * v.z - z * v.y);
            double ty = 2.0 * (z * v.x - x * v.z);
            double tz = 2.0 * (x * v.y - y * v.x);
            return new Vec3(
                    v.x + w * tx + (y * tz - z * ty),
                    v.y + w * ty + (z * tx - x * tz),
                    v.z + w * tz + (x * ty - y * tx)
            );
        }
    }
}
