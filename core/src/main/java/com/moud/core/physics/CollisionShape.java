package com.moud.core.physics;

/** Describes the collision geometry of a physics body. */
public sealed interface CollisionShape permits
        CollisionShape.Box, CollisionShape.Sphere, CollisionShape.Capsule {

    record Box(double halfX, double halfY, double halfZ) implements CollisionShape {}

    record Sphere(double radius) implements CollisionShape {}

    record Capsule(double halfHeight, double radius) implements CollisionShape {}

    static Box box(double halfX, double halfY, double halfZ) {
        return new Box(halfX, halfY, halfZ);
    }

    static Sphere sphere(double radius) {
        return new Sphere(radius);
    }

    static Capsule capsule(double halfHeight, double radius) {
        return new Capsule(halfHeight, radius);
    }
}
