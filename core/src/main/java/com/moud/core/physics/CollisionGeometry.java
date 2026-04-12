package com.moud.core.physics;

public record CollisionGeometry(float[] vertices, int[] indices) {
    public static final CollisionGeometry EMPTY = new CollisionGeometry(new float[0], new int[0]);

    public boolean isEmpty() {
        return vertices == null || indices == null || vertices.length < 9 || indices.length < 3;
    }
}
