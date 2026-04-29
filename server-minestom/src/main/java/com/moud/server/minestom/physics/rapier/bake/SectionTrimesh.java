package com.moud.server.minestom.physics.rapier.bake;

/**
 * Triangle soup produced by the chunk-section bakers. Vertices are XYZ triples
 * in section-local space (0..16 on each axis); indices reference vertex
 * triples (so {@code indices[i]} is a vertex index, not a float offset).
 */
public record SectionTrimesh(float[] vertices, int[] indices) {

    public boolean isEmpty() {
        return indices.length == 0;
    }

    public static SectionTrimesh empty() {
        return new SectionTrimesh(new float[0], new int[0]);
    }
}
