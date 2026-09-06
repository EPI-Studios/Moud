package com.meekdev.moud.core.math;

public record Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    public static Aabb around(Vec3 centre, Vec3 size) {
        double hx = Math.abs(size.x()) * 0.5;
        double hy = Math.abs(size.y()) * 0.5;
        double hz = Math.abs(size.z()) * 0.5;
        return new Aabb(centre.x() - hx, centre.y() - hy, centre.z() - hz,
                centre.x() + hx, centre.y() + hy, centre.z() + hz);
    }

    public boolean intersects(Aabb o) {
        return minX <= o.maxX && maxX >= o.minX
                && minY <= o.maxY && maxY >= o.minY
                && minZ <= o.maxZ && maxZ >= o.minZ;
    }

    public Aabb grow(double by) {
        return new Aabb(minX - by, minY - by, minZ - by, maxX + by, maxY + by, maxZ + by);
    }
}
