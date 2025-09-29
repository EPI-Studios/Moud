package com.moud.api.math;

import org.graalvm.polyglot.HostAccess;

public class Vector3 {
    @HostAccess.Export
    public float x;
    @HostAccess.Export
    public float y;
    @HostAccess.Export
    public float z;

    public Vector3() {
        this.x = 0.0f;
        this.y = 0.0f;
        this.z = 0.0f;
    }

    public Vector3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3(double x, double y, double z) {
        this.x = (float) x;
        this.y = (float) y;
        this.z = (float) z;
    }

    public Vector3(Vector3 other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
    }

    @HostAccess.Export
    public static Vector3 zero() {
        return new Vector3(0.0f, 0.0f, 0.0f);
    }

    @HostAccess.Export
    public static Vector3 one() {
        return new Vector3(1.0f, 1.0f, 1.0f);
    }

    @HostAccess.Export
    public static Vector3 up() {
        return new Vector3(0.0f, 1.0f, 0.0f);
    }

    @HostAccess.Export
    public static Vector3 down() {
        return new Vector3(0.0f, -1.0f, 0.0f);
    }

    @HostAccess.Export
    public static Vector3 left() {
        return new Vector3(-1.0f, 0.0f, 0.0f);
    }

    @HostAccess.Export
    public static Vector3 right() {
        return new Vector3(1.0f, 0.0f, 0.0f);
    }

    @HostAccess.Export
    public static Vector3 forward() {
        return new Vector3(0.0f, 0.0f, 1.0f);
    }

    @HostAccess.Export
    public static Vector3 backward() {
        return new Vector3(0.0f, 0.0f, -1.0f);
    }

    public static Vector3 lerp(Vector3 start, Vector3 end, float t) {
        t = MathUtils.clamp(t, 0.0f, 1.0f);
        return new Vector3(
                MathUtils.lerp(start.x, end.x, t),
                MathUtils.lerp(start.y, end.y, t),
                MathUtils.lerp(start.z, end.z, t)
        );
    }

    @HostAccess.Export
    public Vector3 add(Vector3 other) {
        return new Vector3(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    @HostAccess.Export
    public Vector3 subtract(Vector3 other) {
        return new Vector3(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    @HostAccess.Export
    public Vector3 multiply(double scalar) {
        return new Vector3(this.x * scalar, this.y * scalar, this.z * scalar);
    }

    @HostAccess.Export
    public Vector3 multiply(Vector3 other) {
        return new Vector3(this.x * other.x, this.y * other.y, this.z * other.z);
    }

    @HostAccess.Export
    public Vector3 divide(double scalar) {
        if (Math.abs(scalar) < MathUtils.EPSILON) {
            throw new ArithmeticException("Division by zero or near-zero value");
        }
        return new Vector3(this.x / scalar, this.y / scalar, this.z / scalar);
    }

    @HostAccess.Export
    public Vector3 divide(Vector3 other) {
        return new Vector3(this.x / other.x, this.y / other.y, this.z / other.z);
    }

    @HostAccess.Export
    public Vector3 negate() {
        return new Vector3(-this.x, -this.y, -this.z);
    }

    @HostAccess.Export
    public float dot(Vector3 other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    @HostAccess.Export
    public Vector3 cross(Vector3 other) {
        return new Vector3(
                this.y * other.z - this.z * other.y,
                this.z * other.x - this.x * other.z,
                this.x * other.y - this.y * other.x
        );
    }

    @HostAccess.Export
    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    @HostAccess.Export
    public float lengthSquared() {
        return x * x + y * y + z * z;
    }

    @HostAccess.Export
    public Vector3 normalize() {
        float len = length();
        if (len < MathUtils.EPSILON) {
            return new Vector3(0.0f, 0.0f, 0.0f);
        }
        return new Vector3(x / len, y / len, z / len);
    }

    @HostAccess.Export
    public float distance(Vector3 other) {
        return subtract(other).length();
    }

    @HostAccess.Export
    public float distanceSquared(Vector3 other) {
        return subtract(other).lengthSquared();
    }

    @HostAccess.Export
    public Vector3 lerp(Vector3 target, float t) {
        t = MathUtils.clamp(t, 0.0f, 1.0f);
        return new Vector3(
                MathUtils.lerp(this.x, target.x, t),
                MathUtils.lerp(this.y, target.y, t),
                MathUtils.lerp(this.z, target.z, t)
        );
    }

    @HostAccess.Export
    public Vector3 slerp(Vector3 target, float t) {
        t = MathUtils.clamp(t, 0.0f, 1.0f);
        float dot = MathUtils.clamp(this.normalize().dot(target.normalize()), -1.0f, 1.0f);
        float theta = (float) Math.acos(dot) * t;
        Vector3 relativeVec = target.subtract(this.multiply(dot)).normalize();
        return this.multiply(Math.cos(theta)).add(relativeVec.multiply(Math.sin(theta)));
    }

    @HostAccess.Export
    public Vector3 reflect(Vector3 normal) {
        return this.subtract(normal.multiply(2.0f * this.dot(normal)));
    }

    @HostAccess.Export
    public Vector3 project(Vector3 onto) {
        float ontoLengthSq = onto.lengthSquared();
        if (ontoLengthSq < MathUtils.EPSILON) {
            return Vector3.zero();
        }
        return onto.multiply(this.dot(onto) / ontoLengthSq);
    }

    @HostAccess.Export
    public Vector3 reject(Vector3 onto) {
        return this.subtract(this.project(onto));
    }

    @HostAccess.Export
    public float angle(Vector3 other) {
        float dot = this.normalize().dot(other.normalize());
        dot = MathUtils.clamp(dot, -1.0f, 1.0f);
        return (float) Math.acos(dot);
    }

    @HostAccess.Export
    public Vector3 rotateAroundAxis(Vector3 axis, float angle) {
        return Quaternion.fromAxisAngle(axis, angle).rotate(this);
    }

    @HostAccess.Export
    public Vector3 abs() {
        return new Vector3(Math.abs(x), Math.abs(y), Math.abs(z));
    }

    @HostAccess.Export
    public Vector3 min(Vector3 other) {
        return new Vector3(Math.min(x, other.x), Math.min(y, other.y), Math.min(z, other.z));
    }

    @HostAccess.Export
    public Vector3 max(Vector3 other) {
        return new Vector3(Math.max(x, other.x), Math.max(y, other.y), Math.max(z, other.z));
    }

    @HostAccess.Export
    public Vector3 clamp(Vector3 min, Vector3 max) {
        return new Vector3(
                MathUtils.clamp(x, min.x, max.x),
                MathUtils.clamp(y, min.y, max.y),
                MathUtils.clamp(z, min.z, max.z)
        );
    }

    @HostAccess.Export
    public boolean equals(Vector3 other, float tolerance) {
        return Math.abs(x - other.x) <= tolerance &&
                Math.abs(y - other.y) <= tolerance &&
                Math.abs(z - other.z) <= tolerance;
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

    @Override
    public int hashCode() {
        int result = Float.floatToIntBits(x);
        result = 31 * result + Float.floatToIntBits(y);
        result = 31 * result + Float.floatToIntBits(z);
        return result;
    }

    @Override
    public String toString() {
        return String.format("Vector3(%.3f, %.3f, %.3f)", x, y, z);
    }
}