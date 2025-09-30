package com.moud.api.rendering.mesh;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a mesh sub-section with an optional index buffer.
 */
public final class MeshPart {
    private final String id;
    private final List<MeshVertex> vertices;
    private final int[] indices;

    MeshPart(String id, List<MeshVertex> vertices, int[] indices) {
        this.id = Objects.requireNonNull(id, "id");
        this.vertices = List.copyOf(vertices);
        this.indices = indices != null ? indices.clone() : null;
    }

    public String getId() {
        return id;
    }

    public List<MeshVertex> getVertices() {
        return Collections.unmodifiableList(vertices);
    }

    public boolean isIndexed() {
        return indices != null && indices.length > 0;
    }

    public int[] getIndices() {
        return indices != null ? indices.clone() : null;
    }

    public int getVertexCount() {
        return vertices.size();
    }
}
