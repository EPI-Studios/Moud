package com.moud.client.fabric.render.mesh.cache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientMeshBindings {
    private static final Map<Long, String> BY_NODE = new ConcurrentHashMap<>();

    private ClientMeshBindings() {
    }

    public static void bind(long nodeId, String hash) {
        BY_NODE.put(nodeId, hash);
    }

    public static void unbind(long nodeId) {
        BY_NODE.remove(nodeId);
    }

    public static Optional<String> hashFor(long nodeId) {
        return Optional.ofNullable(BY_NODE.get(nodeId));
    }

    public static void clear() {
        BY_NODE.clear();
    }
}
