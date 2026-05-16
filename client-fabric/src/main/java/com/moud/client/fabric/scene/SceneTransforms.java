package com.moud.client.fabric.scene;

import com.moud.core.math.Quat;
import com.moud.core.math.Transform;
import com.moud.core.math.Vec3;
import com.moud.core.util.ParseUtils;
import com.moud.net.protocol.SceneSnapshot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SceneTransforms {
    private SceneTransforms() {}

    private static final Map<Long, CacheEntry> WORLD_CACHE = new ConcurrentHashMap<>();

    private record CacheEntry(Transform world, long fingerprint) {}

    public static void clearCache() {
        WORLD_CACHE.clear();
    }

    public static void evict(long nodeId) {
        if (nodeId == 0L) return;
        WORLD_CACHE.remove(nodeId);
    }

    public static Transform worldTransform(SceneSnapshot.NodeSnapshot node) {
        if (node == null) return Transform.IDENTITY;
        boolean inherit = shouldInheritTransform(node);

        Transform parentWorld = Transform.IDENTITY;
        long parentFp = 0L;
        if (inherit && node.parentId() != 0L) {
            SceneSnapshot.NodeSnapshot parentNode = ClientSceneBus.getNode(node.parentId());
            if (parentNode != null) {
                parentWorld = worldTransform(parentNode);
                CacheEntry parentEntry = WORLD_CACHE.get(parentNode.nodeId());
                if (parentEntry != null) parentFp = parentEntry.fingerprint;
            }
        }

        long fp = mix(localFingerprint(node), parentFp);
        CacheEntry cached = WORLD_CACHE.get(node.nodeId());
        if (cached != null && cached.fingerprint == fp) return cached.world;

        Transform world = parentWorld.compose(localTransform(node));
        WORLD_CACHE.put(node.nodeId(), new CacheEntry(world, fp));
        return world;
    }

    public static Transform localTransform(SceneSnapshot.NodeSnapshot node) {
        if (node == null) return Transform.IDENTITY;
        Transform3DCell cell = SceneStore.get(node.nodeId());

        double x = cell != null && cell.hasPosition ? cell.px : ParseUtils.parseFloat(getProperty(node, "x"), 0.0f);
        double y = cell != null && cell.hasPosition ? cell.py : ParseUtils.parseFloat(getProperty(node, "y"), 0.0f);
        double z = cell != null && cell.hasPosition ? cell.pz : ParseUtils.parseFloat(getProperty(node, "z"), 0.0f);

        Quat rot;
        if (cell != null && cell.hasRotation) {
            rot = new Quat(cell.qx, cell.qy, cell.qz, cell.qw);
        } else {
            float rx = ParseUtils.parseFloat(getProperty(node, "rx"), 0.0f);
            float ry = ParseUtils.parseFloat(getProperty(node, "ry"), 0.0f);
            float rz = ParseUtils.parseFloat(getProperty(node, "rz"), 0.0f);
            rot = Quat.fromEulerDeg(rx, ry, rz);
        }

        boolean cellHasScale = cell != null && cell.hasScale;
        boolean propHasScale = getProperty(node, "sx") != null
                || getProperty(node, "sy") != null
                || getProperty(node, "sz") != null;
        boolean hasSize = cellHasScale || propHasScale;
        double sx;
        double sy;
        double sz;
        if (cellHasScale) {
            sx = Math.max(1e-6, cell.sx);
            sy = Math.max(1e-6, cell.sy);
            sz = Math.max(1e-6, cell.sz);
        } else if (propHasScale) {
            sx = Math.max(1e-6, ParseUtils.parseFloat(getProperty(node, "sx"), 1.0f));
            sy = Math.max(1e-6, ParseUtils.parseFloat(getProperty(node, "sy"), 1.0f));
            sz = Math.max(1e-6, ParseUtils.parseFloat(getProperty(node, "sz"), 1.0f));
        } else {
            sx = sy = sz = 1.0;
        }
        Vec3 scale = new Vec3(sx, sy, sz);

        Vec3 pivot = hasSize
                ? new Vec3(x + sx * 0.5, y + sy * 0.5, z + sz * 0.5)
                : new Vec3(x, y, z);

        return new Transform(pivot, rot, scale);
    }

    private static long localFingerprint(SceneSnapshot.NodeSnapshot node) {
        Transform3DCell cell = SceneStore.get(node.nodeId());
        long cellEpoch = cell != null ? cell.localEpoch() : 0L;
        long globals = mix(mix(ClientSceneBus.version(), ClientPropertyOverrides.epoch()),
                mix(SceneStore.structureEpoch(), ClientLocalNodes.epoch()));
        return mix(cellEpoch, globals);
    }

    private static long mix(long a, long b) {
        long h = a ^ (b * 0x9E3779B97F4A7C15L);
        h ^= h >>> 32;
        return h;
    }

    private static boolean shouldInheritTransform(SceneSnapshot.NodeSnapshot node) {
        String v = getProperty(node, "@inherit_transform");
        if (v == null || v.isBlank()) return true;
        String s = v.trim().toLowerCase();
        return !("false".equals(s) || "0".equals(s));
    }

    public static String getProperty(SceneSnapshot.NodeSnapshot node, String key) {
        if (node == null) return null;
        String override = ClientPropertyOverrides.get(node.nodeId(), key);
        if (override != null) return override;
        if (node.properties() == null) return null;
        for (SceneSnapshot.Property p : node.properties()) {
            if (p != null && key.equals(p.key())) {
                return p.value();
            }
        }
        return null;
    }
}
