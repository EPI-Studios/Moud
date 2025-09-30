package com.moud.api.rendering.mesh;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable representation of a mesh composed of one or more parts.
 */
public final class Mesh {
    private final List<MeshPart> parts;
    private final Map<String, MeshPart> partsById;

    Mesh(List<MeshPart> parts) {
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("A mesh must contain at least one part");
        }
        this.parts = List.copyOf(parts);
        Map<String, MeshPart> map = new LinkedHashMap<>();
        for (MeshPart part : parts) {
            MeshPart existing = map.putIfAbsent(part.getId(), part);
            if (existing != null) {
                throw new IllegalArgumentException("Duplicate mesh part id: " + part.getId());
            }
        }
        this.partsById = Collections.unmodifiableMap(map);
    }

    public static MeshBuilder builder() {
        return new MeshBuilder();
    }

    public List<MeshPart> getParts() {
        return parts;
    }

    public Optional<MeshPart> findPart(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(partsById.get(id));
    }

    public MeshPart requirePart(String id) {
        return findPart(id).orElseThrow(() -> new IllegalArgumentException("Unknown mesh part: " + id));
    }

    public MeshData toData() {
        return MeshData.fromMesh(this);
    }
}
