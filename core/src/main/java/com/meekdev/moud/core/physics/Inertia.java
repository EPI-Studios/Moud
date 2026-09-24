package com.meekdev.moud.core.physics;

import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;

public record Inertia(double xx, double yy, double zz, double xy, double xz, double yz) {

    public static final Inertia ZERO = new Inertia(0, 0, 0, 0, 0, 0);
    private static final Vector3 BACK = new Vector3(0, 0, 1);
    private static final double SINGULAR = 1.0e-18;

    public static Inertia box(double mass, Vector3 size, Quat rotation) {
        double width = size.x() * size.x();
        double height = size.y() * size.y();
        double depth = size.z() * size.z();
        double share = mass / 12;
        return turned(share * (height + depth), share * (width + depth), share * (width + height), rotation);
    }

    public static Inertia ball(double mass, double radius) {
        double each = 0.4 * mass * radius * radius;
        return new Inertia(each, each, each, 0, 0, 0);
    }

    private static Inertia turned(double aboutRight, double aboutUp, double aboutBack, Quat rotation) {
        Vector3 right = rotation.rotate(Vector3.RIGHT);
        Vector3 up = rotation.rotate(Vector3.UP);
        Vector3 back = rotation.rotate(BACK);
        return new Inertia(
                aboutRight * right.x() * right.x() + aboutUp * up.x() * up.x() + aboutBack * back.x() * back.x(),
                aboutRight * right.y() * right.y() + aboutUp * up.y() * up.y() + aboutBack * back.y() * back.y(),
                aboutRight * right.z() * right.z() + aboutUp * up.z() * up.z() + aboutBack * back.z() * back.z(),
                aboutRight * right.x() * right.y() + aboutUp * up.x() * up.y() + aboutBack * back.x() * back.y(),
                aboutRight * right.x() * right.z() + aboutUp * up.x() * up.z() + aboutBack * back.x() * back.z(),
                aboutRight * right.y() * right.z() + aboutUp * up.y() * up.z() + aboutBack * back.y() * back.z());
    }

    public Inertia shifted(double mass, Vector3 offset) {
        double x = offset.x();
        double y = offset.y();
        double z = offset.z();
        return new Inertia(
                xx + mass * (y * y + z * z),
                yy + mass * (x * x + z * z),
                zz + mass * (x * x + y * y),
                xy - mass * x * y,
                xz - mass * x * z,
                yz - mass * y * z);
    }

    public Inertia plus(Inertia other) {
        return new Inertia(
                xx + other.xx,
                yy + other.yy,
                zz + other.zz,
                xy + other.xy,
                xz + other.xz,
                yz + other.yz);
    }

    public Vector3 times(Vector3 vector) {
        return new Vector3(
                xx * vector.x() + xy * vector.y() + xz * vector.z(),
                xy * vector.x() + yy * vector.y() + yz * vector.z(),
                xz * vector.x() + yz * vector.y() + zz * vector.z());
    }

    public Inertia inverse() {
        double cofactorXX = yy * zz - yz * yz;
        double cofactorXY = xz * yz - xy * zz;
        double cofactorXZ = xy * yz - xz * yy;
        double determinant = xx * cofactorXX + xy * cofactorXY + xz * cofactorXZ;
        if (Math.abs(determinant) < SINGULAR) return ZERO;

        double scale = 1 / determinant;
        return new Inertia(
                cofactorXX * scale,
                (xx * zz - xz * xz) * scale,
                (xx * yy - xy * xy) * scale,
                cofactorXY * scale,
                cofactorXZ * scale,
                (xy * xz - xx * yz) * scale);
    }

    public static Inertia between(Inertia first, Inertia second) {
        return first.inverse().plus(second.inverse()).inverse();
    }
}
