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

    public static Vector3 zero() {
        return new Vector3(0, 0, 0);
    }

    public Vector3 add(Vector3 other) {
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    public Vector3 subtract(Vector3 other) {
        return new Vector3(x - other.x, y - other.y, z - other.z);
    }

    public Vector3 multiply(double scalar) {
        return new Vector3(x * scalar, y * scalar, z * scalar);
    }

    public Vector3 divide(double scalar) {
        if (scalar == 0) throw new IllegalArgumentException("Cannot divide by zero");
        return new Vector3(x / scalar, y / scalar, z / scalar);
    }

    public double dot(Vector3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vector3 cross(Vector3 other) {
        return new Vector3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x
        );
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public Vector3 normalize() {
        double len = length();
        if (len == 0) return new Vector3(0, 0, 0);
        return new Vector3(x / len, y / len, z / len);
    }

    public double distance(Vector3 other) {
        return subtract(other).length();
    }

    public double distanceSquared(Vector3 other) {
        return subtract(other).lengthSquared();
    }

    public static Vector3 lerp(Vector3 start, Vector3 end, float t) {
        return new Vector3(
                (float)(start.x + (end.x - start.x) * t),
                (float)(start.y + (end.y - start.y) * t),
                (float)(start.z + (end.z - start.z) * t)
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Vector3 vector3 = (Vector3) obj;
        return Double.compare(vector3.x, x) == 0 &&
                Double.compare(vector3.y, y) == 0 &&
                Double.compare(vector3.z, z) == 0;
    }

    @Override
    public int hashCode() {
        long temp = Double.doubleToLongBits(x);
        int result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(y);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(z);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return String.format("Vector3(%.3f, %.3f, %.3f)", x, y, z);
    }
}