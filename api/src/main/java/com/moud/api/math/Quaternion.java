package com.moud.api.math;

public class Quaternion {
    public float x, y, z, w;

    public Quaternion() {
        this(0, 0, 0, 1);
    }

    public Quaternion(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    public Quaternion(Vector3 axis, float angle) {
        float halfAngle = angle * 0.5f;
        float s = (float) Math.sin(halfAngle);
        this.w = (float) Math.cos(halfAngle);
        this.x = (float) (axis.x * s);
        this.y = (float) (axis.y * s);
        this.z = (float) (axis.z * s);
    }

    public Quaternion multiply(Quaternion q) {
        return new Quaternion(
                w * q.x + x * q.w + y * q.z - z * q.y,
                w * q.y - x * q.z + y * q.w + z * q.x,
                w * q.z + x * q.y - y * q.x + z * q.w,
                w * q.w - x * q.x - y * q.y - z * q.z
        );
    }

    public Quaternion conjugate() {
        return new Quaternion(-x, -y, -z, w);
    }

    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z + w * w);
    }

    public Quaternion normalize() {
        float len = length();
        if (len == 0) return new Quaternion();
        return new Quaternion(x / len, y / len, z / len, w / len);
    }

    public Quaternion slerp(Quaternion target, float t) {
        float dot = x * target.x + y * target.y + z * target.z + w * target.w;

        if (Math.abs(dot) > 0.9995f) {
            return new Quaternion(
                    x + t * (target.x - x),
                    y + t * (target.y - y),
                    z + t * (target.z - z),
                    w + t * (target.w - w)
            ).normalize();
        }

        if (dot < 0.0f) {
            target = new Quaternion(-target.x, -target.y, -target.z, -target.w);
            dot = -dot;
        }

        dot = Math.max(Math.min(dot, 1.0f), -1.0f);
        float theta = (float) Math.acos(dot) * t;

        Quaternion v2 = target.subtract(multiply(dot)).normalize();

        return multiply((float) Math.cos(theta)).add(v2.multiply((float) Math.sin(theta)));
    }

    private Quaternion subtract(Quaternion q) {
        return new Quaternion(x - q.x, y - q.y, z - q.z, w - q.w);
    }

    private Quaternion add(Quaternion q) {
        return new Quaternion(x + q.x, y + q.y, z + q.z, w + q.w);
    }

    private Quaternion multiply(float scalar) {
        return new Quaternion(x * scalar, y * scalar, z * scalar, w * scalar);
    }

//    public Vector3 rotateVector(Vector3 v) {
//        Quaternion vQ = new Quaternion(v.x, v.y, v.z, 0);
//        Quaternion result = multiply(vQ).multiply(conjugate());
//        return new Vector3(result.x, result.y, result.z);
//    }

    public Vector3 toEulerAngles() {
        float sinr_cosp = 2 * (w * x + y * z);
        float cosr_cosp = 1 - 2 * (x * x + y * y);
        float roll = (float) Math.atan2(sinr_cosp, cosr_cosp);

        float sinp = 2 * (w * y - z * x);
        float pitch;
        if (Math.abs(sinp) >= 1) {
            pitch = (float) Math.copySign(Math.PI / 2, sinp);
        } else {
            pitch = (float) Math.asin(sinp);
        }

        float siny_cosp = 2 * (w * z + x * y);
        float cosy_cosp = 1 - 2 * (y * y + z * z);
        float yaw = (float) Math.atan2(siny_cosp, cosy_cosp);

        return new Vector3(
                (float) Math.toDegrees(roll),
                (float) Math.toDegrees(pitch),
                (float) Math.toDegrees(yaw)
        );
    }

    public static Quaternion fromEulerAngles(float roll, float pitch, float yaw) {
        float cr = (float) Math.cos(Math.toRadians(roll) * 0.5);
        float sr = (float) Math.sin(Math.toRadians(roll) * 0.5);
        float cp = (float) Math.cos(Math.toRadians(pitch) * 0.5);
        float sp = (float) Math.sin(Math.toRadians(pitch) * 0.5);
        float cy = (float) Math.cos(Math.toRadians(yaw) * 0.5);
        float sy = (float) Math.sin(Math.toRadians(yaw) * 0.5);

        return new Quaternion(
                sr * cp * cy - cr * sp * sy,
                cr * sp * cy + sr * cp * sy,
                cr * cp * sy - sr * sp * cy,
                cr * cp * cy + sr * sp * sy
        );
    }

    public static Quaternion identity() {
        return new Quaternion(0, 0, 0, 1);
    }

    @Override
    public String toString() {
        return "Quaternion(" + x + ", " + y + ", " + z + ", " + w + ")";
    }
}