package com.moud.api.math;

public class Vector3 {
    public float x, y, z;

    public Vector3() {
        this(0, 0, 0);
    }

    public Vector3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3(Vector3 other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
    }

    public Vector3 add(Vector3 other) {
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    public Vector3 subtract(Vector3 other) {
        return new Vector3(x - other.x, y - other.y, z - other.z);
    }

    public Vector3 multiply(float scalar) {
        return new Vector3(x * scalar, y * scalar, z * scalar);
    }

    public Vector3 divide(float scalar) {
        return new Vector3(x / scalar, y / scalar, z / scalar);
    }

    public float dot(Vector3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vector3 cross(Vector3 other) {
        return new Vector3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x
        );
    }

    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    public float lengthSquared() {
        return x * x + y * y + z * z;
    }

    public Vector3 normalize() {
        float len = length();
        if (len == 0) return new Vector3();
        return divide(len);
    }

    public Vector3 lerp(Vector3 target, float t) {
        return new Vector3(
                x + (target.x - x) * t,
                y + (target.y - y) * t,
                z + (target.z - z) * t
        );
    }

    public float distance(Vector3 other) {
        return subtract(other).length();
    }

    public float distanceSquared(Vector3 other) {
        return subtract(other).lengthSquared();
    }

    @Override
    public String toString() {
        return "Vector3(" + x + ", " + y + ", " + z + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Vector3 vector3 = (Vector3) obj;
        return Float.compare(vector3.x, x) == 0 &&
                Float.compare(vector3.y, y) == 0 &&
                Float.compare(vector3.z, z) == 0;
    }

    public static Vector3 zero() { return new Vector3(0, 0, 0); }
    public static Vector3 one() { return new Vector3(1, 1, 1); }
    public static Vector3 up() { return new Vector3(0, 1, 0); }
    public static Vector3 down() { return new Vector3(0, -1, 0); }
    public static Vector3 forward() { return new Vector3(0, 0, 1); }
    public static Vector3 back() { return new Vector3(0, 0, -1); }
    public static Vector3 left() { return new Vector3(-1, 0, 0); }
    public static Vector3 right() { return new Vector3(1, 0, 0); }


}