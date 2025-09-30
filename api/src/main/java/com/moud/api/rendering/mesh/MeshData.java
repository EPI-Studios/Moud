package com.moud.api.rendering.mesh;

import com.moud.api.math.Vector3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Serializable representation of a {@link Mesh} that can be sent across the network.
 */
public final class MeshData {
    private final List<PartData> parts;

    public MeshData(List<PartData> parts) {
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("MeshData requires at least one part");
        }
        List<PartData> copy = new ArrayList<>(parts.size());
        for (PartData part : parts) {
            copy.add(Objects.requireNonNull(part, "part"));
        }
        this.parts = Collections.unmodifiableList(copy);
    }

    public List<PartData> parts() {
        return parts;
    }

    public static MeshData fromMesh(Mesh mesh) {
        Objects.requireNonNull(mesh, "mesh");
        List<PartData> partData = new ArrayList<>();
        for (MeshPart part : mesh.getParts()) {
            List<VertexData> vertexData = new ArrayList<>(part.getVertexCount());
            for (MeshVertex vertex : part.getVertices()) {
                vertexData.add(new VertexData(vertex.getPosition(), vertex.getNormal(), vertex.getU(), vertex.getV()));
            }
            int[] indices = part.isIndexed() ? part.getIndices() : null;
            partData.add(new PartData(part.getId(), vertexData, indices));
        }
        return new MeshData(partData);
    }

    public Mesh toMesh() {
        MeshBuilder builder = Mesh.builder();
        for (PartData part : parts) {
            builder.part(part.id(), meshPartBuilder -> {
                for (VertexData vertex : part.vertices()) {
                    meshPartBuilder.vertex(vertexBuilder -> vertexBuilder
                            .position(new Vector3(vertex.position()))
                            .normal(new Vector3(vertex.normal()))
                            .uv(vertex.u(), vertex.v()));
                }
                int[] indices = part.indices();
                if (indices != null && indices.length > 0) {
                    if (indices.length % 3 != 0) {
                        throw new IllegalStateException(
                                "MeshData part '" + part.id() + "' indices length must be a multiple of 3"
                        );
                    }
                    for (int i = 0; i < indices.length; i += 3) {
                        meshPartBuilder.triangle(indices[i], indices[i + 1], indices[i + 2]);
                    }
                }
            });
        }
        return builder.build();
    }

    public PartData part(String id) {
        for (PartData part : parts) {
            if (part.id().equals(id)) {
                return part;
            }
        }
        return null;
    }

    public record PartData(String id, List<VertexData> vertices, int[] indices) {
        public PartData {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(vertices, "vertices");
            vertices = List.copyOf(vertices);
            indices = indices != null ? indices.clone() : null;
        }

        @Override
        public List<VertexData> vertices() {
            return Collections.unmodifiableList(vertices);
        }

        @Override
        public int[] indices() {
            return indices != null ? indices.clone() : null;
        }
    }

    public record VertexData(Vector3 position, Vector3 normal, float u, float v) {
        public VertexData {
            position = new Vector3(Objects.requireNonNull(position, "position"));
            normal = normal != null ? new Vector3(normal) : Vector3.up();
        }
    }
}
