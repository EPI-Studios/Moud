package com.moud.core.math;

public record Quat(double x, double y, double z, double w) {

    public static final Quat IDENTITY = new Quat(0, 0, 0, 1);

    public static Quat fromEulerDeg(float rxDeg, float ryDeg, float rzDeg) {
        double rx = Math.toRadians(Float.isFinite(rxDeg) ? rxDeg : 0.0f);
        double ry = Math.toRadians(Float.isFinite(ryDeg) ? ryDeg : 0.0f);
        double rz = Math.toRadians(Float.isFinite(rzDeg) ? rzDeg : 0.0f);

        double hx = rx * 0.5;
        double hy = ry * 0.5;
        double hz = rz * 0.5;

        double sx = Math.sin(hx), cx = Math.cos(hx);
        double sy = Math.sin(hy), cy = Math.cos(hy);
        double sz = Math.sin(hz), cz = Math.cos(hz);

        Quat qx = new Quat(sx, 0, 0, cx);
        Quat qy = new Quat(0, sy, 0, cy);
        Quat qz = new Quat(0, 0, sz, cz);

        return qz.mul(qy).mul(qx).normalized();
    }

    public Quat mul(Quat b) {
        double nx = w * b.x + x * b.w + y * b.z - z * b.y;
        double ny = w * b.y - x * b.z + y * b.w + z * b.x;
        double nz = w * b.z + x * b.y - y * b.x + z * b.w;
        double nw = w * b.w - x * b.x - y * b.y - z * b.z;
        return new Quat(nx, ny, nz, nw);
    }

    public Quat inverse() {
        double n = x * x + y * y + z * z + w * w;
        if (n <= 0.0) {
            return IDENTITY;
        }
        double inv = 1.0 / n;
        return new Quat(-x * inv, -y * inv, -z * inv, w * inv);
    }

    public Quat normalized() {
        double n = Math.sqrt(x * x + y * y + z * z + w * w);
        if (n <= 0.0) {
            return IDENTITY;
        }
        double inv = 1.0 / n;
        return new Quat(x * inv, y * inv, z * inv, w * inv);
    }

    public Vec3 rotate(Vec3 v) {
        double vx = v.x(), vy = v.y(), vz = v.z();
        double tx = 2.0 * (y * vz - z * vy);
        double ty = 2.0 * (z * vx - x * vz);
        double tz = 2.0 * (x * vy - y * vx);
        return new Vec3(
                vx + w * tx + (y * tz - z * ty),
                vy + w * ty + (z * tx - x * tz),
                vz + w * tz + (x * ty - y * tx)
        );
    }

    public Vec3 toEulerDeg() {
        Quat q = normalized();
        double xx = q.x * q.x;
        double yy = q.y * q.y;
        double zz = q.z * q.z;
        double ww = q.w * q.w;

        double m00 = ww + xx - yy - zz;
        double m01 = 2.0 * (q.x * q.y - q.w * q.z);
        double m02 = 2.0 * (q.x * q.z + q.w * q.y);
        double m10 = 2.0 * (q.x * q.y + q.w * q.z);
        double m11 = ww - xx + yy - zz;
        double m12 = 2.0 * (q.y * q.z - q.w * q.x);
        double m20 = 2.0 * (q.x * q.z - q.w * q.y);
        double m21 = 2.0 * (q.y * q.z + q.w * q.x);
        double m22 = ww - xx - yy + zz;

        double pitchX;
        double yawY;
        double rollZ;

        yawY = Math.asin(clamp(m02, -1.0, 1.0));
        if (Math.abs(Math.cos(yawY)) > 1e-6) {
            pitchX = Math.atan2(-m12, m22);
            rollZ = Math.atan2(-m01, m00);
        } else {
            pitchX = 0.0;
            rollZ = Math.atan2(m10, m11);
        }

        return new Vec3(Math.toDegrees(pitchX), Math.toDegrees(yawY), Math.toDegrees(rollZ));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
