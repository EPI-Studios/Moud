package com.moud.api.math;

import org.graalvm.polyglot.HostAccess;

public class Vector2 {

    @HostAccess.Export
    public float x;
    @HostAccess.Export
    public float y;

    public Vector2() {
        this.x = 0.0f;
        this.y = 0.0f;
    }

    public Vector2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Vector2(double x, double y) {
        this.x = (float) x;
        this.y = (float) y;
    }

    public Vector2(Vector2 other) {
        this.x = other.x;
        this.y = other.y;
    }

    @HostAccess.Export
    public static Vector2 zero() {
        return new Vector2(0.0f, 0.0f);
    }

    @HostAccess.Export
    public static Vector2 one() {
        return new Vector2(1.0f, 1.0f);
    }

    @HostAccess.Export
    public Vector2 add(Vector2 other) {
        return new Vector2(this.x + other.x, this.y + other.y);
    }

    @HostAccess.Export
    public Vector2 subtract(Vector2 other) {
        return new Vector2(this.x - other.x, this.y - other.y);
    }

    @HostAccess.Export
    public Vector2 multiply(double scalar) {
        return new Vector2(this.x * scalar, this.y * scalar);
    }

    @HostAccess.Export
    public Vector2 multiply(Vector2 other) {
        return new Vector2(this.x * other.x, this.y * other.y);
    }

    @HostAccess.Export
    public Vector2 divide(double scalar) {
        if (Math.abs(scalar) < MathUtils.EPSILON) {
            throw new ArithmeticException("Division by zero or near-zero value");
        }
        return new Vector2(this.x / scalar, this.y / scalar);
    }

    @HostAccess.Export
    public Vector2 divide(Vector2 other) {
        return new Vector2(this.x / other.x, this.y / other.y);
    }

    @HostAccess.Export
    public Vector2 negate() {
        return new Vector2(-this.x, -this.y);
    }

    @HostAccess.Export
    public float dot(Vector2 other) {
        return this.x * other.x + this.y * other.y;
    }

    @HostAccess.Export
    public float length() {
        return (float) Math.sqrt(x * x + y * y);

    }

    @HostAccess.Export
    public float lengthSquared() {
        return x * x + y * y;
    }

    @HostAccess.Export
    public Vector2 normalize() {
        float len = length();
        if (len < MathUtils.EPSILON) {
            return new Vector2(0.0f, 0.0f);
        }
        return new Vector2(x / len, y / len);
    }

    @HostAccess.Export
    public float distance(Vector2 other) {
        return subtract(other).length();
    }

    @HostAccess.Export
    public float distanceSquared(Vector2 other) {
        return subtract(other).lengthSquared();
    }

    @HostAccess.Export
    public Vector2 slerp(Vector2 target, float t) {
        t = MathUtils.clamp(t, 0.0f, 1.0f);
        float dot = MathUtils.clamp(this.normalize().dot(target.normalize()), -1.0f, 1.0f);
        float theta = (float) Math.acos(dot) * t;
        Vector2 relativeVec = target.subtract(this.multiply(dot)).normalize();
        return this.multiply(Math.cos(theta)).add(relativeVec.multiply(Math.sin(theta)));
    }


    @HostAccess.Export
    public Vector2 reflect(Vector2 normal) {
        return this.subtract(normal.multiply(2.0f * this.dot(normal)));
    }

    @HostAccess.Export
    public Vector2 project(Vector2 onto) {
        float ontoLengthSq = onto.lengthSquared();
        if (ontoLengthSq < MathUtils.EPSILON) {
            return Vector2.zero();
        }
        return onto.multiply(this.dot(onto) / ontoLengthSq);
    }

    @HostAccess.Export
    public Vector2 reject(Vector2 onto) {
        return this.subtract(this.project(onto));
    }

    @HostAccess.Export
    public float angle(Vector2 other) {
        float dot = this.normalize().dot(other.normalize());
        dot = MathUtils.clamp(dot, -1.0f, 1.0f);
        return (float) Math.acos(dot);
    }


    @HostAccess.Export
    public Vector2 abs() {
        return new Vector2(Math.abs(x), Math.abs(y));
    }

    @HostAccess.Export
    public Vector2 min(Vector2 other) {
        return new Vector2(Math.min(x, other.x), Math.min(y, other.y));
    }

    @HostAccess.Export
    public Vector2 max(Vector2 other) {
        return new Vector2(Math.max(x, other.x), Math.max(y, other.y));
    }

    @HostAccess.Export
    public Vector2 clamp(Vector2 min, Vector2 max) {
        return new Vector2(
                MathUtils.clamp(x, min.x, max.x),
                MathUtils.clamp(y, min.y, max.y)
        );
    }

    @HostAccess.Export
    public boolean equals(Vector2 other, float tolerance) {
        return Math.abs(x - other.x) <= tolerance &&
                Math.abs(y - other.y) <= tolerance;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Vector2 vector2 = (Vector2) obj;
        return Float.compare(vector2.x, x) == 0 &&
                Float.compare(vector2.y, y) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.floatToIntBits(x);
        result = 31 * result + Float.floatToIntBits(y);
        return result;
    }





    @Override
    public String toString() {
        return String.format("Vector2(%.3f, %.3f)", x, y);
    }

}
