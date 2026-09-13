package com.meekdev.moud.core.math;

public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public static final Vec3 ONE = new Vec3(1, 1, 1);
    public static final Vec3 UP = new Vec3(0, 1, 0);
    public static final Vec3 RIGHT = new Vec3(1, 0, 0);
    public static final Vec3 FORWARD = new Vec3(0, 0, -1);

    public Vec3 add(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    public Vec3 sub(Vec3 o) {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    public Vec3 mul(double s) {
        return new Vec3(x * s, y * s, z * s);
    }

    public Vec3 mul(Vec3 o) {
        return new Vec3(x * o.x, y * o.y, z * o.z);
    }

    public Vec3 neg() {
        return new Vec3(-x, -y, -z);
    }

    public double dot(Vec3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vec3 cross(Vec3 o) {
        return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double lengthSq() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSq());
    }

    public double distance(Vec3 o) {
        return sub(o).length();
    }

    public Vec3 normalize() {
        double len = length();
        return len < 1e-12 ? ZERO : mul(1.0 / len);
    }

    public Vec3 lerp(Vec3 o, double t) {
        return new Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
