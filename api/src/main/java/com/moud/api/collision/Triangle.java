package com.moud.api.collision;

import com.moud.api.math.Vector3;

public final class Triangle {
    public final Vector3 v0;
    public final Vector3 v1;
    public final Vector3 v2;
    public final Vector3 normal;
    public final AABB bounds;

    public Triangle(Vector3 v0, Vector3 v1, Vector3 v2) {
        this.v0 = v0;
        this.v1 = v1;
        this.v2 = v2;
        this.normal = computeNormal(v0, v1, v2);
        this.bounds = computeBounds(v0, v1, v2);
    }

    public Triangle(double x0, double y0, double z0,
                    double x1, double y1, double z1,
                    double x2, double y2, double z2) {
        this(new Vector3(x0, y0, z0), new Vector3(x1, y1, z1), new Vector3(x2, y2, z2));
    }

    private static Vector3 computeNormal(Vector3 a, Vector3 b, Vector3 c) {
        Vector3 ab = b.subtract(a);
        Vector3 ac = c.subtract(a);
        Vector3 n = ab.cross(ac);
        float len = n.length();
        if (len < 1.0e-6f) {
            return Vector3.zero();
        }
        return n.multiply(1.0 / len);
    }

    private static AABB computeBounds(Vector3 a, Vector3 b, Vector3 c) {
        double minX = Math.min(a.x, Math.min(b.x, c.x));
        double minY = Math.min(a.y, Math.min(b.y, c.y));
        double minZ = Math.min(a.z, Math.min(b.z, c.z));
        double maxX = Math.max(a.x, Math.max(b.x, c.x));
        double maxY = Math.max(a.y, Math.max(b.y, c.y));
        double maxZ = Math.max(a.z, Math.max(b.z, c.z));
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public Triangle offset(double ox, double oy, double oz) {
        if (ox == 0 && oy == 0 && oz == 0) {
            return this;
        }
        return new Triangle(
                new Vector3(v0.x + ox, v0.y + oy, v0.z + oz),
                new Vector3(v1.x + ox, v1.y + oy, v1.z + oz),
                new Vector3(v2.x + ox, v2.y + oy, v2.z + oz)
        );
    }
}
