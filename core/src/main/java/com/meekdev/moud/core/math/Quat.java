package com.meekdev.moud.core.math;

public record Quat(double x, double y, double z, double w) {

    public static final Quat IDENTITY = new Quat(0, 0, 0, 1);

    public static Quat axisAngle(Vec3 axis, double radians) {
        Vec3 a = axis.normalize();
        double h = radians * 0.5;
        double s = Math.sin(h);
        return new Quat(a.x() * s, a.y() * s, a.z() * s, Math.cos(h));
    }

    public static Quat euler(double pitchX, double yawY, double rollZ) {
        double cx = Math.cos(pitchX * 0.5), sx = Math.sin(pitchX * 0.5);
        double cy = Math.cos(yawY * 0.5), sy = Math.sin(yawY * 0.5);
        double cz = Math.cos(rollZ * 0.5), sz = Math.sin(rollZ * 0.5);
        return new Quat(
                sx * cy * cz + cx * sy * sz,
                cx * sy * cz - sx * cy * sz,
                cx * cy * sz - sx * sy * cz,
                cx * cy * cz + sx * sy * sz).normalize();
    }

    public Quat mul(Quat o) {
        return new Quat(
                w * o.x + x * o.w + y * o.z - z * o.y,
                w * o.y - x * o.z + y * o.w + z * o.x,
                w * o.z + x * o.y - y * o.x + z * o.w,
                w * o.w - x * o.x - y * o.y - z * o.z);
    }

    public Quat conjugate() {
        return new Quat(-x, -y, -z, w);
    }

    public Quat inverse() {
        return conjugate();
    }

    public Vec3 rotate(Vec3 v) {
        Vec3 u = new Vec3(x, y, z);
        double s = w;
        return u.mul(2.0 * u.dot(v))
                .add(v.mul(s * s - u.dot(u)))
                .add(u.cross(v).mul(2.0 * s));
    }

    public double lengthSq() {
        return x * x + y * y + z * z + w * w;
    }

    public Quat normalize() {
        double len = Math.sqrt(lengthSq());
        if (len < 1e-12) return IDENTITY;
        double i = 1.0 / len;
        return new Quat(x * i, y * i, z * i, w * i);
    }

    public Quat slerp(Quat o, double t) {
        double d = x * o.x + y * o.y + z * o.z + w * o.w;
        Quat b = o;
        if (d < 0) {
            b = new Quat(-o.x, -o.y, -o.z, -o.w);
            d = -d;
        }
        if (d > 0.9995) {
            return new Quat(x + (b.x - x) * t, y + (b.y - y) * t,
                    z + (b.z - z) * t, w + (b.w - w) * t).normalize();
        }
        double theta = Math.acos(d);
        double sin = Math.sin(theta);
        double a = Math.sin((1 - t) * theta) / sin;
        double c = Math.sin(t * theta) / sin;
        return new Quat(x * a + b.x * c, y * a + b.y * c, z * a + b.z * c, w * a + b.w * c);
    }

    public static Quat lookAt(Vec3 forward, Vec3 up) {
        Vec3 f = forward.normalize();
        if (f.lengthSq() < 1e-12) return IDENTITY;
        Vec3 r = f.cross(up);
        if (r.lengthSq() < 1e-12) r = f.cross(Vec3.FORWARD);
        r = r.normalize();
        Vec3 u = r.cross(f);
        return fromAxes(r, u, f.neg());
    }

    public static Quat fromAxes(Vec3 xAxis, Vec3 yAxis, Vec3 zAxis) {
        double m00 = xAxis.x(), m01 = yAxis.x(), m02 = zAxis.x();
        double m10 = xAxis.y(), m11 = yAxis.y(), m12 = zAxis.y();
        double m20 = xAxis.z(), m21 = yAxis.z(), m22 = zAxis.z();
        double trace = m00 + m11 + m22;
        if (trace > 0) {
            double s = Math.sqrt(trace + 1.0) * 2;
            return new Quat((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25 * s).normalize();
        }
        if (m00 > m11 && m00 > m22) {
            double s = Math.sqrt(1.0 + m00 - m11 - m22) * 2;
            return new Quat(0.25 * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s).normalize();
        }
        if (m11 > m22) {
            double s = Math.sqrt(1.0 + m11 - m00 - m22) * 2;
            return new Quat((m01 + m10) / s, 0.25 * s, (m12 + m21) / s, (m02 - m20) / s).normalize();
        }
        double s = Math.sqrt(1.0 + m22 - m00 - m11) * 2;
        return new Quat((m02 + m20) / s, (m12 + m21) / s, 0.25 * s, (m10 - m01) / s).normalize();
    }
}
