package com.moud.api.rendering.mesh;

import com.moud.api.math.Vector3;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Builder class for creating immutable {@link Mesh} instances.
 */
public final class MeshBuilder {
    private final List<MeshPart> parts = new ArrayList<>();

    public MeshBuilder part(String id, Consumer<MeshPartBuilder> configurer) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(configurer, "configurer");
        MeshPartBuilder builder = new MeshPartBuilder(id);
        configurer.accept(builder);
        parts.add(builder.build());
        return this;
    }

    public Mesh build() {
        return new Mesh(parts);
    }

    public static final class MeshPartBuilder {
        private final String id;
        private final List<MeshVertex> vertices = new ArrayList<>();
        private final List<Integer> indices = new ArrayList<>();

        private MeshPartBuilder(String id) {
            this.id = id;
        }

        public MeshPartBuilder vertex(Consumer<MeshVertexBuilder> configurer) {
            Objects.requireNonNull(configurer, "configurer");
            MeshVertexBuilder builder = new MeshVertexBuilder();
            configurer.accept(builder);
            vertices.add(builder.build());
            return this;
        }

        public MeshPartBuilder triangle(int a, int b, int c) {
            indices.add(a);
            indices.add(b);
            indices.add(c);
            return this;
        }

        public MeshPartBuilder quad(int a, int b, int c, int d) {
            // Triangulate quad as two triangles for compatibility
            triangle(a, b, c);
            triangle(a, c, d);
            return this;
        }

        private MeshPart build() {
            if (vertices.isEmpty()) {
                throw new IllegalStateException("A mesh part must contain at least one vertex");
            }
            int[] indexArray = indices.isEmpty() ? null : indices.stream().mapToInt(Integer::intValue).toArray();
            return new MeshPart(id, vertices, indexArray);
        }
    }

    public static final class MeshVertexBuilder {
        private Vector3 position;
        private Vector3 normal;
        private float u;
        private float v;
        private boolean hasUv;

        public MeshVertexBuilder position(float x, float y, float z) {
            this.position = new Vector3(x, y, z);
            return this;
        }

        public MeshVertexBuilder position(Vector3 position) {
            this.position = Objects.requireNonNull(position, "position");
            return this;
        }

        public MeshVertexBuilder normal(float x, float y, float z) {
            this.normal = new Vector3(x, y, z);
            return this;
        }

        public MeshVertexBuilder normal(Vector3 normal) {
            this.normal = Objects.requireNonNull(normal, "normal");
            return this;
        }

        public MeshVertexBuilder uv(float u, float v) {
            this.u = u;
            this.v = v;
            this.hasUv = true;
            return this;
        }

        private MeshVertex build() {
            if (position == null) {
                throw new IllegalStateException("Mesh vertex requires a position");
            }
            if (!hasUv) {
                // Default to zero UVs if none were provided
                this.u = 0.0f;
                this.v = 0.0f;
            }
            return new MeshVertex(position, normal, u, v);
        }
    }
}
