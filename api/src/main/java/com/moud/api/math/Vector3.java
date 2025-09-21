package com.moud.api.math;

public class Vector3 {
    public double x, y, z;

    public Vector3() {
        this(0, 0, 0);
    }

    public Vector3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }


    public Vector3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3 add(Vector3 other) {
        return new Vector3(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    public Vector3 multiply(double scalar) {
        return new Vector3(this.x * scalar, this.y * scalar, this.z * scalar);
    }

    public Vector3 normalize() {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length == 0) {
            return new Vector3(0, 0, 0);
        }
        return new Vector3(x / length, y / length, z / length);
    }

    public static Vector3 lerp(Vector3 start, Vector3 end, float t) {
        float factor = Math.max(0.0f, Math.min(1.0f, t));
        return new Vector3(
                start.x + (end.x - start.x) * factor,
                start.y + (end.y - start.y) * factor,
                start.z + (end.z - start.z) * factor
        );
    }

    public static Vector3 zero() {
        return new Vector3(0, 0, 0);
    }

    @Override
    public String toString() {
        return "Vector3{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}