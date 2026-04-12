package com.moud.client.fabric.physics;

import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.core.physics.CollisionGeometry;
import com.moud.core.util.ParseUtils;
import com.moud.net.protocol.CollisionGeometrySnapshot;
import com.moud.net.protocol.SceneSnapshot;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CsgBoxCollisionCache {
    private static volatile CollisionShape[] shapes = new CollisionShape[0];
    private static long cachedVersion = Long.MIN_VALUE;
    private static final Map<Long, List<CollisionGeometry>> collisionGeometryByNode = new HashMap<>();
    private static final int DEFAULT_LAYER = 1;
    private static final int DEFAULT_MASK = 1;

    private CsgBoxCollisionCache() {
    }

    public static CollisionShape[] get() {
        long v = ClientSceneBus.version();
        if (v != cachedVersion) {
            cachedVersion = v;
            shapes = rebuild(ClientSceneBus.copyNodes());
        }
        return shapes;
    }

    public static void onCollisionGeometry(CollisionGeometrySnapshot snapshot) {
        if (snapshot == null || snapshot.nodeId() <= 0L || snapshot.hulls() == null || snapshot.hulls().isEmpty()) {
            return;
        }
        collisionGeometryByNode.put(snapshot.nodeId(), List.copyOf(snapshot.hulls()));
        cachedVersion = Long.MIN_VALUE;
    }

    public static void clearCollisionGeometry() {
        collisionGeometryByNode.clear();
        cachedVersion = Long.MIN_VALUE;
        shapes = new CollisionShape[0];
    }

    private static CollisionShape[] rebuild(List<SceneSnapshot.NodeSnapshot> nodes) {
        if (nodes.isEmpty()) return new CollisionShape[0];

        // Parse local transforms for all nodes (needed to walk the parent chain)
        Map<Long, float[]> locals = new HashMap<>(nodes.size() * 2);
        // [parentId as bits, inherit flag] stored as long[2]
        Map<Long, long[]> parentInfo = new HashMap<>(nodes.size() * 2);
        Map<Long, SceneSnapshot.NodeSnapshot> nodesById = new HashMap<>(nodes.size() * 2);

        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null || node.nodeId() <= 0) continue;
            locals.put(node.nodeId(), parseLocalTransform(node));
            parentInfo.put(node.nodeId(), new long[]{node.parentId(), parseInherit(node) ? 1L : 0L});
            nodesById.put(node.nodeId(), node);
        }

        List<Long> csgBoxIds = new ArrayList<>();
        List<Long> hullNodeIds = new ArrayList<>();
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null || node.nodeId() <= 0 || !collisionEnabled(node)) {
                continue;
            }
            if ("CSGBox".equals(node.type()) || "CSGBlock".equals(node.type())) {
                csgBoxIds.add(node.nodeId());
            }
            List<CollisionGeometry> hulls = collisionGeometryByNode.get(node.nodeId());
            if (hulls != null && !hulls.isEmpty()) {
                hullNodeIds.add(node.nodeId());
            }
        }
        if (csgBoxIds.isEmpty() && hullNodeIds.isEmpty()) return new CollisionShape[0];

        Map<Long, float[]> worldCache = new HashMap<>(nodes.size() * 2);
        Set<Long> visiting = new HashSet<>();
        Quaternionf tempQ = new Quaternionf();
        Vector3f tempV = new Vector3f();

        for (long id : csgBoxIds) {
            computeWorld(id, locals, parentInfo, worldCache, visiting, tempQ, tempV);
        }
        for (long id : hullNodeIds) {
            computeWorld(id, locals, parentInfo, worldCache, visiting, tempQ, tempV);
        }

        List<CollisionShape> result = new ArrayList<>(csgBoxIds.size() + hullNodeIds.size() * 4);
        for (long id : csgBoxIds) {
            float[] w = worldCache.get(id);
            if (w == null) continue;

            float wsx = w[7], wsy = w[8], wsz = w[9];
            if (wsx < 1e-4f || wsy < 1e-4f || wsz < 1e-4f) continue;

            float qx = w[3], qy = w[4], qz = w[5], qw = w[6];
            double hx = wsx * 0.5, hy = wsy * 0.5, hz = wsz * 0.5;

            SceneSnapshot.NodeSnapshot node = nodesById.get(id);
            int layerBits = layer(node);
            int maskBits = mask(node);

            double m00 = 1.0 - 2.0 * (qy * qy + qz * qz);
            double m10 = 2.0 * (qx * qy + qz * qw);
            double m20 = 2.0 * (qx * qz - qy * qw);
            double m01 = 2.0 * (qx * qy - qz * qw);
            double m11 = 1.0 - 2.0 * (qx * qx + qz * qz);
            double m21 = 2.0 * (qy * qz + qx * qw);
            double m02 = 2.0 * (qx * qz + qy * qw);
            double m12 = 2.0 * (qy * qz - qx * qw);
            double m22 = 1.0 - 2.0 * (qx * qx + qy * qy);

            result.add(ObbCollisionShape.of(w[0], w[1], w[2], hx, hy, hz,
                    layerBits, maskBits,
                    m00, m01, m02, m10, m11, m12, m20, m21, m22));
        }

        for (long id : hullNodeIds) {
            float[] w = worldCache.get(id);
            SceneSnapshot.NodeSnapshot node = nodesById.get(id);
            List<CollisionGeometry> hulls = collisionGeometryByNode.get(id);
            if (w == null || node == null || hulls == null || hulls.isEmpty()) {
                continue;
            }

            float qx = w[3], qy = w[4], qz = w[5], qw = w[6];
            double m00 = 1.0 - 2.0 * (qy * qy + qz * qz);
            double m10 = 2.0 * (qx * qy + qz * qw);
            double m20 = 2.0 * (qx * qz - qy * qw);
            double m01 = 2.0 * (qx * qy - qz * qw);
            double m11 = 1.0 - 2.0 * (qx * qx + qz * qz);
            double m21 = 2.0 * (qy * qz + qx * qw);
            double m02 = 2.0 * (qx * qz + qy * qw);
            double m12 = 2.0 * (qy * qz - qx * qw);
            double m22 = 1.0 - 2.0 * (qx * qx + qy * qy);

            int layerBits = layer(node);
            int maskBits = mask(node);

            for (CollisionGeometry hull : hulls) {
                CollisionShape shape = hullShape(hull, w, layerBits, maskBits);
                if (shape != null) {
                    result.add(shape);
                }
            }
        }

        return result.toArray(new CollisionShape[0]);
    }

    private static CollisionShape hullShape(CollisionGeometry hull, float[] world, int layerBits, int maskBits) {
        if (hull == null || hull.vertices() == null || hull.vertices().length < 9 || world == null || world.length < 10) {
            return null;
        }
        float[] vertices = hull.vertices();

        float px = world[0];
        float py = world[1];
        float pz = world[2];
        float qx = world[3];
        float qy = world[4];
        float qz = world[5];
        float qw = world[6];
        float sx = world[7];
        float sy = world[8];
        float sz = world[9];

        double m00 = 1.0 - 2.0 * (qy * qy + qz * qz);
        double m10 = 2.0 * (qx * qy + qz * qw);
        double m20 = 2.0 * (qx * qz - qy * qw);
        double m01 = 2.0 * (qx * qy - qz * qw);
        double m11 = 1.0 - 2.0 * (qx * qx + qz * qz);
        double m21 = 2.0 * (qy * qz + qx * qw);
        double m02 = 2.0 * (qx * qz + qy * qw);
        double m12 = 2.0 * (qy * qz - qx * qw);
        double m22 = 1.0 - 2.0 * (qx * qx + qy * qy);

        float[] worldVertices = new float[vertices.length];
        for (int i = 0; i + 2 < vertices.length; i += 3) {
            double lx = vertices[i] * sx;
            double ly = vertices[i + 1] * sy;
            double lz = vertices[i + 2] * sz;
            worldVertices[i] = (float) (px + m00 * lx + m01 * ly + m02 * lz);
            worldVertices[i + 1] = (float) (py + m10 * lx + m11 * ly + m12 * lz);
            worldVertices[i + 2] = (float) (pz + m20 * lx + m21 * ly + m22 * lz);
        }
        return ConvexCollisionShape.of(worldVertices, hull.indices(), layerBits, maskBits);
    }

    private static void computeWorld(long nodeId, Map<Long, float[]> locals, Map<Long, long[]> parentInfo,
                                     Map<Long, float[]> cache, Set<Long> visiting,
                                     Quaternionf tempQ, Vector3f tempV) {
        if (cache.containsKey(nodeId)) return;

        float[] local = locals.get(nodeId);
        if (local == null) {
            cache.put(nodeId, new float[]{0, 0, 0, 0, 0, 0, 1, 1, 1, 1});
            return;
        }

        long[] pi = parentInfo.get(nodeId);
        long parentId = pi != null ? pi[0] : 0L;
        boolean inherit = pi != null && pi[1] != 0L;

        float px = local[0], py = local[1], pz = local[2];
        float qx = local[3], qy = local[4], qz = local[5], qw = local[6];
        float sx = local[7], sy = local[8], sz = local[9];

        if (inherit && parentId > 0L) {
            if (!visiting.add(nodeId)) {
                cache.put(nodeId, new float[]{px, py, pz, qx, qy, qz, qw, sx, sy, sz});
                return;
            }
            computeWorld(parentId, locals, parentInfo, cache, visiting, tempQ, tempV);
            visiting.remove(nodeId);

            float[] pw = cache.get(parentId);
            if (pw != null) {
                float ppx = pw[0], ppy = pw[1], ppz = pw[2];
                float pqx = pw[3], pqy = pw[4], pqz = pw[5], pqw = pw[6];
                float psx = pw[7], psy = pw[8], psz = pw[9];

                tempV.set(px * psx, py * psy, pz * psz);
                tempQ.set(pqx, pqy, pqz, pqw).transform(tempV);
                px = ppx + tempV.x;
                py = ppy + tempV.y;
                pz = ppz + tempV.z;

                tempQ.set(pqx, pqy, pqz, pqw).mul(qx, qy, qz, qw).normalize();
                qx = tempQ.x; qy = tempQ.y; qz = tempQ.z; qw = tempQ.w;

                sx = psx * sx;
                sy = psy * sy;
                sz = psz * sz;
            }
        }

        cache.put(nodeId, new float[]{px, py, pz, qx, qy, qz, qw, sx, sy, sz});
    }

    private static float[] parseLocalTransform(SceneSnapshot.NodeSnapshot node) {
        float x = 0, y = 0, z = 0;
        float rxDeg = 0, ryDeg = 0, rzDeg = 0;
        float sx = 1, sy = 1, sz = 1;
        boolean hasScale = false;

        List<SceneSnapshot.Property> props = node.properties();
        if (props != null) {
            for (SceneSnapshot.Property p : props) {
                if (p == null || p.key() == null) continue;
                switch (p.key()) {
                    case "x" -> x = ParseUtils.parseFloat(p.value(), 0);
                    case "y" -> y = ParseUtils.parseFloat(p.value(), 0);
                    case "z" -> z = ParseUtils.parseFloat(p.value(), 0);
                    case "rx" -> rxDeg = ParseUtils.parseFloat(p.value(), 0);
                    case "ry" -> ryDeg = ParseUtils.parseFloat(p.value(), 0);
                    case "rz" -> rzDeg = ParseUtils.parseFloat(p.value(), 0);
                    case "sx" -> { sx = ParseUtils.parseFloat(p.value(), 1); hasScale = true; }
                    case "sy" -> { sy = ParseUtils.parseFloat(p.value(), 1); hasScale = true; }
                    case "sz" -> { sz = ParseUtils.parseFloat(p.value(), 1); hasScale = true; }
                    default -> { }
                }
            }
        }

        sx = safeScale(sx);
        sy = safeScale(sy);
        sz = safeScale(sz);

        boolean minCornerPivot = "CSGBox".equals(node.type()) || "CSGBlock".equals(node.type());
        if (minCornerPivot && hasScale) {
            x += sx * 0.5f;
            y += sy * 0.5f;
            z += sz * 0.5f;
        }

        float rxRad = (float) Math.toRadians(rxDeg);
        float ryRad = (float) Math.toRadians(ryDeg);
        float rzRad = (float) Math.toRadians(rzDeg);
        Quaternionf q = new Quaternionf().rotationZ(rzRad)
                .mul(new Quaternionf().rotationY(ryRad))
                .mul(new Quaternionf().rotationX(rxRad))
                .normalize();

        return new float[]{x, y, z, q.x, q.y, q.z, q.w, sx, sy, sz};
    }

    private static boolean collisionEnabled(SceneSnapshot.NodeSnapshot node) {
        List<SceneSnapshot.Property> props = node.properties();
        if (props == null) return true;
        for (SceneSnapshot.Property p : props) {
            if (p == null || p.key() == null) {
                continue;
            }
            if ("solid".equals(p.key()) || "collision".equals(p.key())) {
                String v = p.value();
                if (v == null) return true;
                v = v.trim().toLowerCase();
                return !("false".equals(v) || "0".equals(v));
            }
        }
        return true;
    }

    private static boolean parseInherit(SceneSnapshot.NodeSnapshot node) {
        List<SceneSnapshot.Property> props = node.properties();
        if (props == null) return true;
        for (SceneSnapshot.Property p : props) {
            if (p != null && "@inherit_transform".equals(p.key())) {
                String v = p.value();
                if (v == null) return true;
                v = v.trim().toLowerCase();
                return !("false".equals(v) || "0".equals(v));
            }
        }
        return true;
    }

    private static int layer(SceneSnapshot.NodeSnapshot node) {
        return parseBits(node, "collision_layer", DEFAULT_LAYER);
    }

    private static int mask(SceneSnapshot.NodeSnapshot node) {
        return parseBits(node, "collision_mask", DEFAULT_MASK);
    }

    private static int parseBits(SceneSnapshot.NodeSnapshot node, String key, int fallback) {
        if (node == null || key == null) return fallback;
        List<SceneSnapshot.Property> props = node.properties();
        if (props == null) return fallback;
        String alias = alternatePropertyKey(key);

        if (alias != null) {
            for (SceneSnapshot.Property p : props) {
                if (p == null || !alias.equals(p.key())) {
                    continue;
                }
                return parseBitsValue(p.value(), fallback);
            }
        }

        for (SceneSnapshot.Property p : props) {
            if (p == null || !key.equals(p.key())) {
                continue;
            }
            return parseBitsValue(p.value(), fallback);
        }

        return fallback;
    }

    private static int parseBitsValue(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int bits = Integer.parseInt(value.trim());
            if (bits <= 0) return 0;
            return bits == Integer.MIN_VALUE ? 0 : Math.abs(bits);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String alternatePropertyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (key.indexOf('_') >= 0) {
            StringBuilder out = new StringBuilder(key.length());
            boolean upperNext = false;
            for (int i = 0; i < key.length(); i++) {
                char c = key.charAt(i);
                if (c == '_') {
                    upperNext = true;
                    continue;
                }
                out.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            }
            return out.toString();
        }
        StringBuilder out = new StringBuilder(key.length() + 4);
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isUpperCase(c)) {
                out.append('_').append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static float safeScale(float v) {
        if (!Float.isFinite(v)) return 1.0f;
        v = Math.abs(v) < 1e-6f ? 0.0f : v;
        return Math.max(1e-6f, v);
    }
}
