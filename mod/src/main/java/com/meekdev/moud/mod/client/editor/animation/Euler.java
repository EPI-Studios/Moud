package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;

public final class Euler {

    private static final double GIMBAL = 0.9999999;

    private Euler() {}

    public static Quat toQuat(Vector3 degrees) {
        return Quat.euler(Math.toRadians(degrees.x()), Math.toRadians(degrees.y()), Math.toRadians(degrees.z()));
    }

    public static Vector3 toDegrees(Quat rotation) {
        Quat q = rotation.normalize();
        double x = q.x();
        double y = q.y();
        double z = q.z();
        double w = q.w();
        double m00 = 1 - 2 * (y * y + z * z);
        double m02 = 2 * (x * z + y * w);
        double m10 = 2 * (x * y + z * w);
        double m11 = 1 - 2 * (x * x + z * z);
        double m12 = 2 * (y * z - x * w);
        double m20 = 2 * (x * z - y * w);
        double m22 = 1 - 2 * (x * x + y * y);
        double pitch = Math.asin(Math.clamp(-m12, -1, 1));
        double yaw;
        double roll;
        if (Math.abs(m12) < GIMBAL) {
            yaw = Math.atan2(m02, m22);
            roll = Math.atan2(m10, m11);
        } else {
            yaw = Math.atan2(-m20, m00);
            roll = 0;
        }
        return new Vector3(Math.toDegrees(pitch), Math.toDegrees(yaw), Math.toDegrees(roll));
    }

    public static Vector3 nearest(Quat rotation, Vector3 previous) {
        Vector3 primary = toDegrees(rotation);
        Vector3 flipped = new Vector3(180 - primary.x(), primary.y() + 180, primary.z() + 180);
        Vector3 a = wrapNear(primary, previous);
        Vector3 b = wrapNear(flipped, previous);
        return a.sub(previous).lengthSq() <= b.sub(previous).lengthSq() ? a : b;
    }

    private static Vector3 wrapNear(Vector3 value, Vector3 near) {
        return new Vector3(wrap(value.x(), near.x()), wrap(value.y(), near.y()), wrap(value.z(), near.z()));
    }

    private static double wrap(double value, double near) {
        return value + 360 * Math.round((near - value) / 360);
    }
}
