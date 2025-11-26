package com.moud.client.collision;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class Triangle {
    public final Vec3d v0;
    public final Vec3d v1;
    public final Vec3d v2;
    public final Vec3d normal;
    public final Box bounds;

    public Triangle(Vec3d v0, Vec3d v1, Vec3d v2) {
        this.v0 = v0;
        this.v1 = v1;
        this.v2 = v2;
        this.normal = computeNormal(v0, v1, v2);
        this.bounds = computeBounds(v0, v1, v2);
    }

    private Vec3d computeNormal(Vec3d a, Vec3d b, Vec3d c) {
        Vec3d ab = b.subtract(a);
        Vec3d ac = c.subtract(a);
        Vec3d n = ab.crossProduct(ac);
        double len = n.length();
        if (len < 1.0e-6) {
            return Vec3d.ZERO;
        }
        return n.multiply(1.0 / len);
    }

    private Box computeBounds(Vec3d a, Vec3d b, Vec3d c) {
        double minX = Math.min(a.x, Math.min(b.x, c.x));
        double minY = Math.min(a.y, Math.min(b.y, c.y));
        double minZ = Math.min(a.z, Math.min(b.z, c.z));
        double maxX = Math.max(a.x, Math.max(b.x, c.x));
        double maxY = Math.max(a.y, Math.max(b.y, c.y));
        double maxZ = Math.max(a.z, Math.max(b.z, c.z));
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
