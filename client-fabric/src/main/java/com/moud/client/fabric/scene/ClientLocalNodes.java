package com.moud.client.fabric.scene;

import com.moud.net.protocol.SceneSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// Negative id = client-local node (server-authoritative ids are positive).
public final class ClientLocalNodes {
    private static final int MAX_NODES = 1024;
    private static final int MAX_PROPS_PER_NODE = 64;
    private static final AtomicLong NEXT_ID = new AtomicLong(-1L);
    private static final AtomicLong EPOCH = new AtomicLong(1L);
    private static final Map<Long, Entry> NODES = new ConcurrentHashMap<>();

    private ClientLocalNodes() {
    }

    public static long epoch() {
        return EPOCH.get();
    }

    private static void bumpEpoch() {
        long next = EPOCH.incrementAndGet();
        if (next <= 0L) EPOCH.set(1L);
    }

    public static long create(String type, String name, long parentId) {
        if (type == null || type.isBlank()) return 0L;
        if (NODES.size() >= MAX_NODES) return 0L;
        long id = NEXT_ID.getAndDecrement();
        NODES.put(id, new Entry(id, parentId, name == null ? "" : name, type));
        bumpEpoch();
        return id;
    }

    public static boolean free(long id) {
        if (id >= 0L) return false;
        if (NODES.remove(id) == null) return false;
        SceneStore.remove(id);
        SceneTransforms.evict(id);
        bumpEpoch();
        return true;
    }

    public static void setProperty(long id, String key, String value) {
        if (id >= 0L || key == null || key.isEmpty()) return;
        Entry e = NODES.get(id);
        if (e == null) return;
        String prev;
        if (value == null) {
            prev = e.props.remove(key);
            if (prev == null) return;
        } else {
            if (!e.props.containsKey(key) && e.props.size() >= MAX_PROPS_PER_NODE) return;
            prev = e.props.put(key, value);
            if (value.equals(prev)) return;
        }
        bumpEpoch();
        if (Transform3DMirror.isTransformKey(key)) {
            Transform3DMirror.apply(id, key, value);
        }
    }

    public static void clearAll() {
        if (NODES.isEmpty()) return;
        for (Long id : NODES.keySet()) {
            SceneStore.remove(id);
            SceneTransforms.evict(id);
        }
        NODES.clear();
        bumpEpoch();
    }

    public static boolean exists(long id) {
        return id < 0L && NODES.containsKey(id);
    }

    public static List<SceneSnapshot.NodeSnapshot> snapshot() {
        if (NODES.isEmpty()) return List.of();
        ArrayList<SceneSnapshot.NodeSnapshot> out = new ArrayList<>(NODES.size());
        for (Entry e : NODES.values()) {
            ArrayList<SceneSnapshot.Property> props = new ArrayList<>(e.props.size());
            for (Map.Entry<String, String> p : e.props.entrySet()) {
                props.add(new SceneSnapshot.Property(p.getKey(), p.getValue()));
            }
            out.add(new SceneSnapshot.NodeSnapshot(e.id, e.parentId, e.name, e.type, List.copyOf(props)));
        }
        return List.copyOf(out);
    }

    private static final class Entry {
        final long id;
        final long parentId;
        final String name;
        final String type;
        final LinkedHashMap<String, String> props = new LinkedHashMap<>();

        Entry(long id, long parentId, String name, String type) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
            this.type = type;
        }
    }
}
