package com.moud.server.minestom.scene;

import com.moud.core.math.Quat;
import com.moud.core.math.Transform;
import com.moud.core.math.Vec3;
import com.moud.core.util.ParseUtils;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SceneSnapshot.NodeSnapshot;
import com.moud.net.protocol.SceneSnapshot.Property;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AoiFilter {
    public static final double DEFAULT_RADIUS = 256.0;
    public static final double DEFAULT_RESEND_THRESHOLD = 8.0;

    private AoiFilter() {}

    public static double readRadius(SceneSnapshot snapshot) {
        Float v = readWorldEnvFloat(snapshot, "aoi_radius");
        return v == null || v <= 0.0f ? DEFAULT_RADIUS : v;
    }

    public static double readResendThreshold(SceneSnapshot snapshot) {
        Float v = readWorldEnvFloat(snapshot, "aoi_resend_threshold");
        return v == null || v < 0.0f ? DEFAULT_RESEND_THRESHOLD : v;
    }

    public static SceneSnapshot filter(SceneSnapshot snapshot, double cx, double cy, double cz, double radius) {
        if (snapshot == null) return null;
        List<NodeSnapshot> nodes = snapshot.nodes();
        if (nodes == null || nodes.isEmpty() || radius <= 0.0) return snapshot;

        Map<Long, NodeSnapshot> byId = new HashMap<>(nodes.size());
        for (NodeSnapshot n : nodes) byId.put(n.nodeId(), n);

        Map<Long, Transform> worldXf = new HashMap<>(nodes.size());
        Set<Long> positionless = new HashSet<>();
        Map<Long, Boolean> alwaysCache = new HashMap<>(nodes.size());
        Set<Long> kept = new HashSet<>(nodes.size());

        for (NodeSnapshot n : nodes) {
            if (kept.contains(n.nodeId())) continue;
            if (isGlobalNodeType(n) || ancestorAlwaysRelevant(n, byId, alwaysCache)) {
                addWithAncestors(n, byId, kept);
                continue;
            }
            Transform wx = worldTransform(n, byId, worldXf, positionless);
            if (wx == null) {
                addWithAncestors(n, byId, kept);
                continue;
            }
            double extra = nodeInfluenceRadius(n);
            double effective = radius + extra;
            double dx = wx.pos().x() - cx, dy = wx.pos().y() - cy, dz = wx.pos().z() - cz;
            if (dx * dx + dy * dy + dz * dz <= effective * effective) {
                addWithAncestors(n, byId, kept);
            }
        }

        List<NodeSnapshot> out = new ArrayList<>(kept.size());
        for (NodeSnapshot n : nodes) {
            if (kept.contains(n.nodeId())) out.add(n);
        }
        return new SceneSnapshot(snapshot.requestId(), snapshot.revision(), List.copyOf(out));
    }

    private static boolean isGlobalNodeType(NodeSnapshot n) {
        return "DirectionalLight3D".equals(n.type());
    }

    private static double nodeInfluenceRadius(NodeSnapshot n) {
        double extent = Math.max(0.0, ParseUtils.parseFloat(stringProp(n, "aoi_extent"), 0.0f));
        String type = n.type();
        if ("OmniLight3D".equals(type)) {
            extent = Math.max(extent, ParseUtils.parseFloat(stringProp(n, "radius"), 0.0f));
        } else if ("SpotLight3D".equals(type)) {
            extent = Math.max(extent, ParseUtils.parseFloat(stringProp(n, "distance"), 0.0f));
        }
        return extent;
    }

    private static Transform worldTransform(NodeSnapshot n, Map<Long, NodeSnapshot> byId,
                                            Map<Long, Transform> cache, Set<Long> positionless) {
        Long id = n.nodeId();
        Transform cached = cache.get(id);
        if (cached != null) return cached;
        if (positionless.contains(id)) return null;

        Transform local = localTransform(n);
        Transform parentXf = null;
        long pid = n.parentId();
        if (pid != 0L) {
            NodeSnapshot p = byId.get(pid);
            if (p != null) parentXf = worldTransform(p, byId, cache, positionless);
        }

        if (local == null && parentXf == null) {
            positionless.add(id);
            return null;
        }
        Transform world = parentXf == null
                ? local
                : (local == null ? parentXf : parentXf.compose(local));
        cache.put(id, world);
        return world;
    }

    private static Transform localTransform(NodeSnapshot n) {
        if (!hasOwnTransform(n)) return null;
        double x = ParseUtils.parseFloat(stringProp(n, "x"), 0.0f);
        double y = ParseUtils.parseFloat(stringProp(n, "y"), 0.0f);
        double z = ParseUtils.parseFloat(stringProp(n, "z"), 0.0f);
        float rx = ParseUtils.parseFloat(stringProp(n, "rx"), 0.0f);
        float ry = ParseUtils.parseFloat(stringProp(n, "ry"), 0.0f);
        float rz = ParseUtils.parseFloat(stringProp(n, "rz"), 0.0f);
        boolean hasSize = stringProp(n, "sx") != null
                || stringProp(n, "sy") != null
                || stringProp(n, "sz") != null;
        double sx = hasSize ? Math.max(1e-6, ParseUtils.parseFloat(stringProp(n, "sx"), 1.0f)) : 1.0;
        double sy = hasSize ? Math.max(1e-6, ParseUtils.parseFloat(stringProp(n, "sy"), 1.0f)) : 1.0;
        double sz = hasSize ? Math.max(1e-6, ParseUtils.parseFloat(stringProp(n, "sz"), 1.0f)) : 1.0;
        return new Transform(new Vec3(x, y, z), Quat.fromEulerDeg(rx, ry, rz), new Vec3(sx, sy, sz));
    }

    private static boolean hasOwnTransform(NodeSnapshot n) {
        return stringProp(n, "x") != null
                || stringProp(n, "y") != null
                || stringProp(n, "z") != null
                || stringProp(n, "rx") != null
                || stringProp(n, "ry") != null
                || stringProp(n, "rz") != null;
    }

    private static void addWithAncestors(NodeSnapshot n, Map<Long, NodeSnapshot> byId, Set<Long> kept) {
        NodeSnapshot cur = n;
        while (cur != null && kept.add(cur.nodeId())) {
            long pid = cur.parentId();
            if (pid == 0L) break;
            cur = byId.get(pid);
        }
    }

    private static boolean ancestorAlwaysRelevant(NodeSnapshot n, Map<Long, NodeSnapshot> byId, Map<Long, Boolean> cache) {
        NodeSnapshot cur = n;
        while (cur != null) {
            Long id = cur.nodeId();
            Boolean cached = cache.get(id);
            boolean self;
            if (cached != null) {
                self = cached;
            } else {
                self = "true".equalsIgnoreCase(stringProp(cur, "always_relevant"))
                        || "1".equals(stringProp(cur, "always_relevant"));
                cache.put(id, self);
            }
            if (self) return true;
            long pid = cur.parentId();
            if (pid == 0L) break;
            cur = byId.get(pid);
        }
        return false;
    }

    private static Float readWorldEnvFloat(SceneSnapshot snapshot, String key) {
        if (snapshot == null || snapshot.nodes() == null) return null;
        for (NodeSnapshot n : snapshot.nodes()) {
            if (n == null) continue;
            if (!"WorldEnvironment".equals(n.type()) && !"WorldEnvironment".equals(n.name())) continue;
            String v = stringProp(n, key);
            if (v == null) return null;
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String stringProp(NodeSnapshot n, String key) {
        if (n == null || key == null || n.properties() == null) return null;
        for (Property p : n.properties()) {
            if (p != null && key.equals(p.key())) return p.value();
        }
        return null;
    }
}
