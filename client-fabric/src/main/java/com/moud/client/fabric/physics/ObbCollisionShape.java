package com.moud.client.fabric.physics;

// column-major rotation: u0=(m00,m10,m20), u1=(m01,m11,m21), u2=(m02,m12,m22)
public record ObbCollisionShape(
        double cx, double cy, double cz,
        double hx, double hy, double hz,
        int layerBits, int maskBits,
        double m00, double m01, double m02,
        double m10, double m11, double m12,
        double m20, double m21, double m22
) {
    private static final double EPSILON = 1e-9;

    // returns MTV to push AABB out of this OBB, or null if no intersection (6-axis SAT)
    public double[] computeMtv(double ax, double ay, double az, double fw, double fh, double fd) {
        double tx = ax - cx, ty = ay - cy, tz = az - cz;
        double minOv = Double.MAX_VALUE;
        double mvX = 0, mvY = 0, mvZ = 0;
        double ov, t, s;

        // World X axis
        t = tx;
        ov = hx * Math.abs(m00) + hy * Math.abs(m01) + hz * Math.abs(m02) + fw - Math.abs(t);
        if (ov <= EPSILON) return null;
        if (ov < minOv) { minOv = ov; s = t >= 0 ? 1.0 : -1.0; mvX = ov * s; mvY = 0; mvZ = 0; }

        // World Y axis
        t = ty;
        ov = hx * Math.abs(m10) + hy * Math.abs(m11) + hz * Math.abs(m12) + fh - Math.abs(t);
        if (ov <= EPSILON) return null;
        if (ov < minOv) { minOv = ov; s = t >= 0 ? 1.0 : -1.0; mvX = 0; mvY = ov * s; mvZ = 0; }

        // World Z axis
        t = tz;
        ov = hx * Math.abs(m20) + hy * Math.abs(m21) + hz * Math.abs(m22) + fd - Math.abs(t);
        if (ov <= EPSILON) return null;
        if (ov < minOv) { minOv = ov; s = t >= 0 ? 1.0 : -1.0; mvX = 0; mvY = 0; mvZ = ov * s; }

        // OBB local X axis u0 = (m00, m10, m20)
        t = tx * m00 + ty * m10 + tz * m20;
        ov = hx + fw * Math.abs(m00) + fh * Math.abs(m10) + fd * Math.abs(m20) - Math.abs(t);
        if (ov <= EPSILON) return null;
        if (ov < minOv) { minOv = ov; s = t >= 0 ? 1.0 : -1.0; mvX = ov * m00 * s; mvY = ov * m10 * s; mvZ = ov * m20 * s; }

        // OBB local Y axis u1 = (m01, m11, m21)
        t = tx * m01 + ty * m11 + tz * m21;
        ov = hy + fw * Math.abs(m01) + fh * Math.abs(m11) + fd * Math.abs(m21) - Math.abs(t);
        if (ov <= EPSILON) return null;
        if (ov < minOv) { minOv = ov; s = t >= 0 ? 1.0 : -1.0; mvX = ov * m01 * s; mvY = ov * m11 * s; mvZ = ov * m21 * s; }

        // OBB local Z axis u2 = (m02, m12, m22)
        t = tx * m02 + ty * m12 + tz * m22;
        ov = hz + fw * Math.abs(m02) + fh * Math.abs(m12) + fd * Math.abs(m22) - Math.abs(t);
        if (ov <= EPSILON) return null;
        if (ov < minOv) { minOv = ov; s = t >= 0 ? 1.0 : -1.0; mvX = ov * m02 * s; mvY = ov * m12 * s; mvZ = ov * m22 * s; }

        return new double[]{mvX, mvY, mvZ};
    }
}
