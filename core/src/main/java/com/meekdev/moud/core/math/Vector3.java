package com.meekdev.moud.core.math;

public record Vector3(double x, double y, double z) {

    public static final Vector3 ZERO = new Vector3(0, 0, 0);
    public static final Vector3 ONE = new Vector3(1, 1, 1);
    public static final Vector3 UP = new Vector3(0, 1, 0);
    public static final Vector3 RIGHT = new Vector3(1, 0, 0);
    public static final Vector3 FORWARD = new Vector3(0, 0, -1);

    public Vector3 add(Vector3 o) {
        return new Vector3(x + o.x, y + o.y, z + o.z);
    }

    public Vector3 sub(Vector3 o) {
        return new Vector3(x - o.x, y - o.y, z - o.z);
    }

    public Vector3 mul(double s) {
        return new Vector3(x * s, y * s, z * s);
    }

    public Vector3 mul(Vector3 o) {
        return new Vector3(x * o.x, y * o.y, z * o.z);
    }

    public Vector3 neg() {
        return new Vector3(-x, -y, -z);
    }

    public double dot(Vector3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vector3 cross(Vector3 o) {
        return new Vector3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double lengthSq() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSq());
    }

    public double distance(Vector3 o) {
        return sub(o).length();
    }

    public Vector3 normalize() {
        double len = length();
        return len < 1e-12 ? ZERO : mul(1.0 / len);
    }

    public Vector3 lerp(Vector3 o, double t) {
        return new Vector3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
