package com.moud.server.minestom.engine.csg;

import com.moud.core.csg.CsgVoxelizer;
import com.moud.core.math.Quat;
import com.moud.core.math.Transform;
import com.moud.core.math.Vec3;
import com.moud.core.scene.Node;
import com.moud.core.scene.SceneTree;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.moud.server.minestom.engine.Engine;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;

public final class CsgBlockWriter {
    private final InstanceContainer instance;
    private final Engine engine;
    private long lastAppliedRevision = Long.MIN_VALUE;
    private Map<Long, Block> lastBlocks = new HashMap<>();

    public CsgBlockWriter(InstanceContainer instance, Engine engine) {
        this.instance = Objects.requireNonNull(instance, "instance");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    private static int roundToInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Math.round(Float.parseFloat(value.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            float v = Float.parseFloat(value.trim());
            return Float.isFinite(v) ? v : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean parseBool(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String v = value.trim().toLowerCase();
        if ("true".equals(v) || "1".equals(v) || "t".equals(v) || "yes".equals(v) || "y".equals(v)) {
            return true;
        }
        if ("false".equals(v) || "0".equals(v) || "f".equals(v) || "no".equals(v) || "n".equals(v)) {
            return false;
        }
        return fallback;
    }

    private static String defaulted(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static long pack(int x, int y, int z) {
        long lx = x & 0x1FFFFF;
        long ly = y & 0x3FFFFF;
        long lz = z & 0x1FFFFF;
        return lx | (ly << 21) | (lz << (21 + 22));
    }

    private static int signExtend(int value, int bits) {
        int shift = 32 - bits;
        return (value << shift) >> shift;
    }

    private static int unpackX(long key) {
        return signExtend((int) (key & 0x1FFFFF), 21);
    }

    private static int unpackY(long key) {
        return signExtend((int) ((key >>> 21) & 0x3FFFFF), 22);
    }

    private static int unpackZ(long key) {
        return signExtend((int) ((key >>> (21 + 22)) & 0x1FFFFF), 21);
    }

    public void tick() {
        long rev = engine.csgRevision();
        if (rev == lastAppliedRevision) {
            return;
        }
        lastAppliedRevision = rev;
        applySnapshot();
    }

    private int applySnapshot() {
        Map<Long, Block> next = new HashMap<>();
        SceneTree tree = engine.sceneTree();
        collect(tree.root(), Transform.IDENTITY, next);

        int removed = 0;
        for (Map.Entry<Long, Block> entry : lastBlocks.entrySet()) {
            long key = entry.getKey();
            if (!next.containsKey(key)) {
                removed++;
                int x = unpackX(key);
                int y = unpackY(key);
                int z = unpackZ(key);
                instance.setBlock(x, y, z, Block.AIR);
            }
        }

        int changed = 0;
        for (Map.Entry<Long, Block> entry : next.entrySet()) {
            long key = entry.getKey();
            Block block = entry.getValue();
            Block prev = lastBlocks.get(key);
            if (prev != null && prev.equals(block)) {
                continue;
            }
            changed++;
            int x = unpackX(key);
            int y = unpackY(key);
            int z = unpackZ(key);
            instance.setBlock(x, y, z, block);
        }

        lastBlocks = next;
        return next.size();
    }

    private void collect(Node node, Transform parentWorld, Map<Long, Block> out) {
        String typeId = engine.nodeTypes().typeIdFor(node);
        Transform local = localTransform(node, typeId);
        Transform world = shouldInheritTransform(node) ? parentWorld.compose(local) : local;

        if ("CSGBlock".equals(typeId)) {
            emitCsgBlock(node, world, out);
        }
        for (Node child : node.children()) {
            collect(child, world, out);
        }
    }

    private void emitCsgBlock(Node node, Transform world, Map<Long, Block> out) {
        if (!parseBool(node.getProperty("solid"), true)) {
            return;
        }

        int sx = Math.max(1, (int) Math.round(world.scale.x));
        int sy = Math.max(1, (int) Math.round(world.scale.y));
        int sz = Math.max(1, (int) Math.round(world.scale.z));

        int x = (int) Math.round(world.pos.x - sx / 2.0);
        int y = (int) Math.round(world.pos.y - sy / 2.0);
        int z = (int) Math.round(world.pos.z - sz / 2.0);

        Vec3 euler = world.rot.toEulerDeg();
        float rxDeg = (float) euler.x;
        float ryDeg = (float) euler.y;
        float rzDeg = (float) euler.z;
        String blockId = defaulted(node.getProperty("block"), "minecraft:stone");

        Block block = Block.fromNamespaceId(blockId);
        if (block == null) {
            block = Block.STONE;
        }

        Block finalBlock = block;
        CsgVoxelizer.forEachVoxel(
                new CsgVoxelizer.VoxelDefinition(x, y, z, sx, sy, sz, rxDeg, ryDeg, rzDeg),
                (xx, yy, zz) -> out.put(pack(xx, yy, zz), finalBlock)
        );
    }

    private static boolean shouldInheritTransform(Node node) {
        if (node == null) {
            return false;
        }
        String v = node.getProperty("@inherit_transform");
        if (v == null || v.isBlank()) {
            return true;
        }
        String s = v.trim().toLowerCase();
        return !("false".equals(s) || "0".equals(s));
    }

    private static Transform localTransform(Node node, String typeId) {
        if (node == null) {
            return Transform.IDENTITY;
        }
        float x = parseFloat(node.getProperty("x"), 0.0f);
        float y = parseFloat(node.getProperty("y"), 0.0f);
        float z = parseFloat(node.getProperty("z"), 0.0f);

        float rxDeg = parseFloat(node.getProperty("rx"), 0.0f);
        float ryDeg = parseFloat(node.getProperty("ry"), 0.0f);
        float rzDeg = parseFloat(node.getProperty("rz"), 0.0f);
        Quat rot = Quat.fromEulerDeg(rxDeg, ryDeg, rzDeg);

        String sxRaw = node.getProperty("sx");
        String syRaw = node.getProperty("sy");
        String szRaw = node.getProperty("sz");
        boolean hasScale = sxRaw != null || syRaw != null || szRaw != null;

        double sx = hasScale ? safeScale(parseFloat(sxRaw, 1.0f)) : 1.0;
        double sy = hasScale ? safeScale(parseFloat(syRaw, 1.0f)) : 1.0;
        double sz = hasScale ? safeScale(parseFloat(szRaw, 1.0f)) : 1.0;
        Vec3 scale = new Vec3(sx, sy, sz);

        boolean pivotIsMinCorner = "CSGBox".equals(typeId) || "CSGBlock".equals(typeId);
        double px = x;
        double py = y;
        double pz = z;
        if (pivotIsMinCorner && hasScale) {
            px = x + sx * 0.5;
            py = y + sy * 0.5;
            pz = z + sz * 0.5;
        }
        return new Transform(new Vec3(px, py, pz), rot, scale);
    }

    private static double safeScale(double value) {
        if (!Double.isFinite(value)) {
            return 1.0;
        }
        double v = Math.abs(value) < 1e-6 ? 0.0 : value;
        return Math.max(1e-6, v);
    }

    private record Transform(Vec3 pos, Quat rot, Vec3 scale) {
        static final Transform IDENTITY = new Transform(new Vec3(0, 0, 0), Quat.IDENTITY, new Vec3(1, 1, 1));

        Transform compose(Transform child) {
            Vec3 scaled = new Vec3(child.pos.x * scale.x, child.pos.y * scale.y, child.pos.z * scale.z);
            Vec3 worldPos = pos.add(rot.rotate(scaled));
            Quat worldRot = rot.mul(child.rot).normalized();
            Vec3 worldScale = scale.mul(child.scale);
            return new Transform(worldPos, worldRot, worldScale);
        }
    }

    private record Vec3(double x, double y, double z) {
        Vec3 add(Vec3 o) {
            return new Vec3(x + o.x, y + o.y, z + o.z);
        }

        Vec3 mul(Vec3 o) {
            return new Vec3(x * o.x, y * o.y, z * o.z);
        }
    }

    private record Quat(double x, double y, double z, double w) {
        static final Quat IDENTITY = new Quat(0, 0, 0, 1);

        static Quat fromEulerDeg(float rxDeg, float ryDeg, float rzDeg) {
            double rx = Math.toRadians(Float.isFinite(rxDeg) ? rxDeg : 0.0f);
            double ry = Math.toRadians(Float.isFinite(ryDeg) ? ryDeg : 0.0f);
            double rz = Math.toRadians(Float.isFinite(rzDeg) ? rzDeg : 0.0f);

            double hx = rx * 0.5;
            double hy = ry * 0.5;
            double hz = rz * 0.5;

            double sx = Math.sin(hx), cx = Math.cos(hx);
            double sy = Math.sin(hy), cy = Math.cos(hy);
            double sz = Math.sin(hz), cz = Math.cos(hz);

            Quat qx = new Quat(sx, 0, 0, cx);
            Quat qy = new Quat(0, sy, 0, cy);
            Quat qz = new Quat(0, 0, sz, cz);

            return qz.mul(qy).mul(qx).normalized();
        }

        Quat mul(Quat b) {
            double nx = w * b.x + x * b.w + y * b.z - z * b.y;
            double ny = w * b.y - x * b.z + y * b.w + z * b.x;
            double nz = w * b.z + x * b.y - y * b.x + z * b.w;
            double nw = w * b.w - x * b.x - y * b.y - z * b.z;
            return new Quat(nx, ny, nz, nw);
        }

        Quat normalized() {
            double n = Math.sqrt(x * x + y * y + z * z + w * w);
            if (n <= 0.0) {
                return IDENTITY;
            }
            double inv = 1.0 / n;
            return new Quat(x * inv, y * inv, z * inv, w * inv);
        }

        Vec3 rotate(Vec3 v) {
            double vx = v.x, vy = v.y, vz = v.z;
            double tx = 2.0 * (y * vz - z * vy);
            double ty = 2.0 * (z * vx - x * vz);
            double tz = 2.0 * (x * vy - y * vx);
            return new Vec3(
                    vx + w * tx + (y * tz - z * ty),
                    vy + w * ty + (z * tx - x * tz),
                    vz + w * tz + (x * ty - y * tx)
            );
        }

        Vec3 toEulerDeg() {
            Quat q = normalized();
            double xx = q.x * q.x;
            double yy = q.y * q.y;
            double zz = q.z * q.z;
            double ww = q.w * q.w;

            double m00 = ww + xx - yy - zz;
            double m01 = 2.0 * (q.x * q.y - q.w * q.z);
            double m02 = 2.0 * (q.x * q.z + q.w * q.y);
            double m10 = 2.0 * (q.x * q.y + q.w * q.z);
            double m11 = ww - xx + yy - zz;
            double m12 = 2.0 * (q.y * q.z - q.w * q.x);
            double m22 = ww - xx - yy + zz;

            double pitchX;
            double yawY;
            double rollZ;

            // For Rz * Ry * Rx:
            yawY = Math.asin(clamp(m02, -1.0, 1.0));
            if (Math.abs(Math.cos(yawY)) > 1e-6) {
                pitchX = Math.atan2(-m12, m22);
                rollZ = Math.atan2(-m01, m00);
            } else {
                pitchX = 0.0;
                rollZ = Math.atan2(m10, m11);
            }

            return new Vec3(Math.toDegrees(pitchX), Math.toDegrees(yawY), Math.toDegrees(rollZ));
        }

        private static double clamp(double v, double min, double max) {
            return Math.max(min, Math.min(max, v));
        }
    }
}
