package com.moud.core.mesh;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MeshRegistry {
    private static final MeshRegistry INSTANCE = new MeshRegistry();

    private final Map<String, ArrayMesh> byHash = new ConcurrentHashMap<>();

    private MeshRegistry() {
    }

    public static MeshRegistry instance() {
        return INSTANCE;
    }

    public ArrayMesh register(ArrayMesh mesh) {
        var existing = byHash.putIfAbsent(mesh.hash(), mesh);
        return existing != null ? existing : mesh;
    }

    public Optional<ArrayMesh> get(String hash) {
        return Optional.ofNullable(byHash.get(hash));
    }

    public boolean contains(String hash) {
        return byHash.containsKey(hash);
    }
}
