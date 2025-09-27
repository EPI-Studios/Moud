package com.moud.api.math;

import org.graalvm.polyglot.HostAccess;

public class Quaternion {
    @HostAccess.Export
    public float x;
    @HostAccess.Export
    public float y;
    @HostAccess.Export
    public float z;
    @HostAccess.Export
    public float w;

    public Quaternion() {
        this.x = 0.0f;
        this.y = 0.0f;
        this.z = 0.0f;
        this.w = 1.0f;
    }

    public Quaternion(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    public Quaternion(Quaternion other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        this.w = other.w;
    }

    @HostAccess.Export
    public static Quaternion identity() {
        return new Quaternion(0.0f, 0.0f, 0.0f, 1.0f);
    }

    @HostAccess.Export
    public static Quaternion fromEuler(float pitch, float yaw, float roll) {
        float pitchRad = MathUtils.toRadians(pitch * 0.5f);
        float yawRad = MathUtils.toRadians(yaw * 0.5f);
        float rollRad = MathUtils.toRadians(roll * 0.5f);

        float cp = MathUtils.cos(pitchRad);
        float sp = MathUtils.sin(pitchRad);
        float cy = MathUtils.cos(yawRad);
        float sy = MathUtils.sin(yawRad);
        float cr = MathUtils.cos(rollRad);
        float sr = MathUtils.sin(rollRad);

        return new Quaternion(
                sr * cp * cy - cr * sp * sy,
                cr * sp * cy + sr * cp * sy,
                cr * cp * sy - sr * sp * cy,
                cr * cp * cy + sr * sp * sy
        );
    }

    @HostAccess.Export
    public static Quaternion fromAxisAngle(Vector3 axis, float angle) {
        Vector3 normalizedAxis = axis.normalize();
        float halfAngle = MathUtils.toRadians(angle * 0.5f);
        float s = MathUtils.sin(halfAngle);

        return new Quaternion(
                normalizedAxis.x * s,
                normalizedAxis.y * s,
                normalizedAxis.z * s,
                MathUtils.cos(halfAngle)
        );
    }

    @HostAccess.Export
    public static Quaternion fromToRotation(Vector3 from, Vector3 to) {
        Vector3 fromNorm = from.normalize();
        Vector3 toNorm = to.normalize();

        float dot = fromNorm.dot(toNorm);

        if (dot < -0.999999f) {
            Vector3 orthogonal = Vector3.up();
            if (MathUtils.abs(fromNorm.dot(orthogonal)) > 0.999999f) {
                orthogonal = Vector3.right();
            }
            Vector3 axis = fromNorm.cross(orthogonal).normalize();
            return fromAxisAngle(axis, 180.0f);
        } else if (dot > 0.999999f) {
            return identity();
        } else {
            Vector3 axis = fromNorm.cross(toNorm);
            float w = MathUtils.sqrt((1.0f + dot) * 2.0f);
            float invW = 1.0f / w;

            return new Quaternion(
                    axis.x * invW,
                    axis.y * invW,
                    axis.z * invW,
                    w * 0.5f
            );
        }
    }

    @HostAccess.Export
    public static Quaternion lookRotation(Vector3 forward, Vector3 up) {
        Vector3 forwardNorm = forward.normalize();
        Vector3 rightNorm = up.normalize().cross(forwardNorm).normalize();
        Vector3 upNorm = forwardNorm.cross(rightNorm);

        float m00 = rightNorm.x;
        float m01 = rightNorm.y;
        float m02 = rightNorm.z;
        float m10 = upNorm.x;
        float m11 = upNorm.y;
        float m12 = upNorm.z;
        float m20 = forwardNorm.x;
        float m21 = forwardNorm.y;
        float m22 = forwardNorm.z;

        float trace = m00 + m11 + m22;

        if (trace > 0.0f) {
            float s = MathUtils.sqrt(trace + 1.0f) * 2.0f;
            return new Quaternion(
                    (m12 - m21) / s,
                    (m20 - m02) / s,
                    (m01 - m10) / s,
                    0.25f * s
            );
        } else if (m00 > m11 && m00 > m22) {
            float s = MathUtils.sqrt(1.0f + m00 - m11 - m22) * 2.0f;
            return new Quaternion(
                    0.25f * s,
                    (m01 + m10) / s,
                    (m20 + m02) / s,
                    (m12 - m21) / s
            );
        } else if (m11 > m22) {
            float s = MathUtils.sqrt(1.0f + m11 - m00 - m22) * 2.0f;
            return new Quaternion(
                    (m01 + m10) / s,
                    0.25f * s,
                    (m12 + m21) / s,
                    (m20 - m02) / s
            );
        } else {
            float s = MathUtils.sqrt(1.0f + m22 - m00 - m11) * 2.0f;
            return new Quaternion(
                    (m20 + m02) / s,
                    (m12 + m21) / s,
                    0.25f * s,
                    (m01 - m10) / s
            );
        }
    }

    @HostAccess.Export
    public Quaternion multiply(Quaternion other) {
        return new Quaternion(
                w * other.x + x * other.w + y * other.z - z * other.y,
                w * other.y - x * other.z + y * other.w + z * other.x,
                w * other.z + x * other.y - y * other.x + z * other.w,
                w * other.w - x * other.x - y * other.y - z * other.z
        );
    }

    @HostAccess.Export
    public Quaternion add(Quaternion other) {
        return new Quaternion(x + other.x, y + other.y, z + other.z, w + other.w);
    }

    @HostAccess.Export
    public Quaternion subtract(Quaternion other) {
        return new Quaternion(x - other.x, y - other.y, z - other.z, w - other.w);
    }

    @HostAccess.Export
    public Quaternion scale(float scalar) {
        return new Quaternion(x * scalar, y * scalar, z * scalar, w * scalar);
    }

    @HostAccess.Export
    public float dot(Quaternion other) {
        return x * other.x + y * other.y + z * other.z + w * other.w;
    }

    @HostAccess.Export
    public float length() {
        return MathUtils.sqrt(x * x + y * y + z * z + w * w);
    }

    @HostAccess.Export
    public float lengthSquared() {
        return x * x + y * y + z * z + w * w;
    }

    @HostAccess.Export
    public Quaternion normalize() {
        float len = length();
        if (len < MathUtils.EPSILON) {
            return identity();
        }
        return new Quaternion(x / len, y / len, z / len, w / len);
    }

    @HostAccess.Export
    public Quaternion conjugate() {
        return new Quaternion(-x, -y, -z, w);
    }

    @HostAccess.Export
    public Quaternion inverse() {
        float lengthSq = lengthSquared();
        if (lengthSq < MathUtils.EPSILON) {
            return identity();
        }
        Quaternion conj = conjugate();
        return new Quaternion(conj.x / lengthSq, conj.y / lengthSq, conj.z / lengthSq, conj.w / lengthSq);
    }

    @HostAccess.Export
    public Vector3 rotate(Vector3 point) {
        Quaternion pointQ = new Quaternion(point.x, point.y, point.z, 0.0f);
        Quaternion result = this.multiply(pointQ).multiply(this.conjugate());
        return new Vector3(result.x, result.y, result.z);
    }

    @HostAccess.Export
    public Vector3 toEuler() {
        float sinr_cosp = 2 * (w * x + y * z);
        float cosr_cosp = 1 - 2 * (x * x + y * y);
        float roll = MathUtils.toDegrees(MathUtils.atan2(sinr_cosp, cosr_cosp));

        float sinp = 2 * (w * y - z * x);
        float pitch;
        if (MathUtils.abs(sinp) >= 1) {
            pitch = MathUtils.toDegrees(MathUtils.sign(sinp) * MathUtils.HALF_PI);
        } else {
            pitch = MathUtils.toDegrees(MathUtils.asin(sinp));
        }

        float siny_cosp = 2 * (w * z + x * y);
        float cosy_cosp = 1 - 2 * (y * y + z * z);
        float yaw = MathUtils.toDegrees(MathUtils.atan2(siny_cosp, cosy_cosp));

        return new Vector3(pitch, yaw, roll);
    }

    @HostAccess.Export
    public Vector3 getAxis() {
        if (MathUtils.abs(w) >= 1.0f) {
            return Vector3.up();
        }

        float s = MathUtils.sqrt(1.0f - w * w);
        if (s < MathUtils.EPSILON) {
            return Vector3.up();
        }

        return new Vector3(x / s, y / s, z / s);
    }

    @HostAccess.Export
    public float getAngle() {
        return MathUtils.toDegrees(2.0f * MathUtils.acos(MathUtils.abs(w)));
    }

    @HostAccess.Export
    public Quaternion slerp(Quaternion target, float t) {
        t = MathUtils.clamp(t, 0.0f, 1.0f);

        Quaternion q1 = this.normalize();
        Quaternion q2 = target.normalize();

        float dot = q1.dot(q2);

        if (dot < 0.0f) {
            q2 = q2.scale(-1.0f);
            dot = -dot;
        }

        if (dot > 0.9995f) {
            Quaternion result = q1.add(q2.subtract(q1).scale(t));
            return result.normalize();
        }

        float theta0 = MathUtils.acos(dot);
        float theta = theta0 * t;

        Quaternion q3 = q2.subtract(q1.scale(dot)).normalize();

        return q1.scale(MathUtils.cos(theta)).add(q3.scale(MathUtils.sin(theta)));
    }

    @HostAccess.Export
    public Quaternion lerp(Quaternion target, float t) {
        t = MathUtils.clamp(t, 0.0f, 1.0f);

        float dot = this.dot(target);
        Quaternion result;

        if (dot < 0.0f) {
            result = new Quaternion(
                    x + t * (-target.x - x),
                    y + t * (-target.y - y),
                    z + t * (-target.z - z),
                    w + t * (-target.w - w)
            );
        } else {
            result = new Quaternion(
                    x + t * (target.x - x),
                    y + t * (target.y - y),
                    z + t * (target.z - z),
                    w + t * (target.w - w)
            );
        }

        return result.normalize();
    }

    @HostAccess.Export
    public float angleTo(Quaternion target) {
        float dot = MathUtils.abs(this.normalize().dot(target.normalize()));
        dot = MathUtils.clamp(dot, 0.0f, 1.0f);
        return MathUtils.toDegrees(2.0f * MathUtils.acos(dot));
    }

    @HostAccess.Export
    public boolean equals(Quaternion other, float tolerance) {
        return MathUtils.abs(x - other.x) <= tolerance &&
                MathUtils.abs(y - other.y) <= tolerance &&
                MathUtils.abs(z - other.z) <= tolerance &&
                MathUtils.abs(w - other.w) <= tolerance;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Quaternion that = (Quaternion) obj;
        return Float.compare(that.x, x) == 0 &&
                Float.compare(that.y, y) == 0 &&
                Float.compare(that.z, z) == 0 &&
                Float.compare(that.w, w) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.floatToIntBits(x);
        result = 31 * result + Float.floatToIntBits(y);
        result = 31 * result + Float.floatToIntBits(z);
        result = 31 * result + Float.floatToIntBits(w);
        return result;
    }

    @Override
    public String toString() {
        return String.format("Quaternion(%.3f, %.3f, %.3f, %.3f)", x, y, z, w);
    }
}