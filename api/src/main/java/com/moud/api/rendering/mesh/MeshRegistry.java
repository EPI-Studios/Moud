package com.moud.api.rendering.mesh;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for reusable meshes.
 */
public final class MeshRegistry {
    private final Map<String, Mesh> meshes = new ConcurrentHashMap<>();

    public Optional<Mesh> find(String id) {
        return Optional.ofNullable(meshes.get(id));
    }

    public Mesh require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown mesh: " + id));
    }

    public void register(String id, Mesh mesh) {
        meshes.put(id, mesh);
    }

    public void unregister(String id) {
        meshes.remove(id);
    }

    public Collection<Mesh> all() {
        return meshes.values();
    }
}
