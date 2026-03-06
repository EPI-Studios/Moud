package com.moud.core.math;


public record Vec3(double x, double y, double z) {

    public Vec3 add(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    public Vec3 mul(Vec3 o) {
        return new Vec3(x * o.x, y * o.y, z * o.z);
    }

    public double dot(Vec3 o) {
        if (o == null) {
            return 0.0;
        }
        return x * o.x + y * o.y + z * o.z;
    }

    public Vec3 cross(Vec3 o) {
        if (o == null) {
            return new Vec3(0, 0, 0);
        }
        return new Vec3(
                y * o.z - z * o.y,
                z * o.x - x * o.z,
                x * o.y - y * o.x
        );
    }
}
