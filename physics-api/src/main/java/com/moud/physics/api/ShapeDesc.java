package com.moud.physics.api;

public sealed interface ShapeDesc {
    record Box(Vec3 halfExtents)                    implements ShapeDesc {}
    record Sphere(float radius)                     implements ShapeDesc {}
    record Capsule(float radius, float halfHeight)  implements ShapeDesc {}
    record Trimesh(float[] vertices, int[] indices) implements ShapeDesc {}
}
