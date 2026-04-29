package com.moud.server.minestom.scene;

import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SceneSnapshot.NodeSnapshot;
import com.moud.net.protocol.SceneSnapshotDelta;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AoiSnapshotTracker {
    private final Map<Long, Integer> sentFingerprints = new HashMap<>();

    public boolean isEmpty() {
        return sentFingerprints.isEmpty();
    }

    public void clear() {
        sentFingerprints.clear();
    }

    public SceneSnapshotDelta diffAndUpdate(SceneSnapshot filtered) {
        if (filtered == null) return null;
        List<NodeSnapshot> nodes = filtered.nodes() == null ? List.of() : filtered.nodes();

        Map<Long, Integer> nextFingerprints = new HashMap<>(nodes.size());
        List<NodeSnapshot> upserts = new ArrayList<>();
        for (NodeSnapshot n : nodes) {
            int fp = fingerprint(n);
            nextFingerprints.put(n.nodeId(), fp);
            Integer prev = sentFingerprints.get(n.nodeId());
            if (prev == null || prev != fp) {
                upserts.add(n);
            }
        }

        List<Long> removed = new ArrayList<>();
        Set<Long> nextIds = nextFingerprints.keySet();
        for (Long id : sentFingerprints.keySet()) {
            if (!nextIds.contains(id)) removed.add(id);
        }

        if (upserts.isEmpty() && removed.isEmpty()) {
            return null;
        }

        sentFingerprints.clear();
        sentFingerprints.putAll(nextFingerprints);
        return new SceneSnapshotDelta(filtered.revision(), List.copyOf(upserts), List.copyOf(removed));
    }

    private static int fingerprint(NodeSnapshot n) {
        int h = 1;
        h = 31 * h + Long.hashCode(n.parentId());
        h = 31 * h + (n.name() == null ? 0 : n.name().hashCode());
        h = 31 * h + (n.type() == null ? 0 : n.type().hashCode());
        if (n.properties() != null) {
            int propHash = 0;
            for (SceneSnapshot.Property p : n.properties()) {
                if (p == null) continue;
                int kh = p.key() == null ? 0 : p.key().hashCode();
                int vh = p.value() == null ? 0 : p.value().hashCode();
                propHash += kh ^ (vh * 1_000_003);
            }
            h = 31 * h + propHash;
        }
        if (n.uniforms() != null) {
            int uniHash = 0;
            for (SceneSnapshot.Uniform u : n.uniforms()) {
                if (u == null) continue;
                int kh = u.key() == null ? 0 : u.key().hashCode();
                int vh = u.values() == null ? 0 : u.values().hashCode();
                uniHash += kh ^ (vh * 1_000_003);
            }
            h = 31 * h + uniHash;
        }
        return h;
    }
}
