package com.moud.client.editor.runtime;

import net.minecraft.util.math.Vec3d;


public final class Capsule {
    private final Vec3d a;
    private final Vec3d b;
    private final double radius;

    private Capsule(Vec3d a, Vec3d b, double radius) {
        this.a = a;
        this.b = b;
        this.radius = radius;
    }

    public static Capsule line(Vec3d a, Vec3d b, double radius) {
        return new Capsule(a, b, radius);
    }

    public static Capsule sphere(Vec3d center, double radius) {
        return new Capsule(center, center, radius);
    }

    public Vec3d a() {
        return a;
    }

    public Vec3d b() {
        return b;
    }

    public double radius() {
        return radius;
    }
}
