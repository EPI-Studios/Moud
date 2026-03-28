package com.moud.client.fabric.physics;

import net.minecraft.util.math.Box;

// column-major rotation: u0=(m00,m10,m20), u1=(m01,m11,m21), u2=(m02,m12,m22)
// a** fields are pre-baked |m**| — computed once in the factory, avoids Math.abs at query time.
// worldAabb is the axis-aligned bounding box wrapping the OBB — used for cheap broad-phase culling.
public record ObbCollisionShape(
        double cx,  double cy,  double cz,
        double hx,  double hy,  double hz,
        int layerBits, int maskBits,
        double m00, double m01, double m02,
        double m10, double m11, double m12,
        double m20, double m21, double m22,
        double a00, double a01, double a02,
        double a10, double a11, double a12,
        double a20, double a21, double a22,
        Box worldAabb
) {
    private static final double EPSILON = 1e-6;

    public static ObbCollisionShape of(
            double cx, double cy, double cz,
            double hx, double hy, double hz,
            int layerBits, int maskBits,
            double m00, double m01, double m02,
            double m10, double m11, double m12,
            double m20, double m21, double m22) {

        double a00 = Math.abs(m00), a01 = Math.abs(m01), a02 = Math.abs(m02);
        double a10 = Math.abs(m10), a11 = Math.abs(m11), a12 = Math.abs(m12);
        double a20 = Math.abs(m20), a21 = Math.abs(m21), a22 = Math.abs(m22);

        // Project OBB half-extents onto each world axis to get the AABB half-extents.
        double wx = hx*a00 + hy*a01 + hz*a02;
        double wy = hx*a10 + hy*a11 + hz*a12;
        double wz = hx*a20 + hy*a21 + hz*a22;
        Box aabb = new Box(cx-wx, cy-wy, cz-wz, cx+wx, cy+wy, cz+wz);

        return new ObbCollisionShape(
                cx, cy, cz, hx, hy, hz, layerBits, maskBits,
                m00, m01, m02, m10, m11, m12, m20, m21, m22,
                a00, a01, a02, a10, a11, a12, a20, a21, a22,
                aabb);
    }

    /**
     * 15-axis SAT (3 world + 3 OBB local + 9 cross-product axes).
     * Returns the minimum MTV to push the AABB out of this OBB, or null if separated.
     *
     * OBB radii for the cross-product axes are derived via the scalar triple product:
     *   u_k · (e_i × u_j) = e_i · (u_j × u_k)
     * which reduces to a single rotation-matrix element (e.g. for i=0, j=0: hy*a02 + hz*a01).
     */
    public double[] computeMtv(double ax, double ay, double az, double fw, double fh, double fd) {
        double tx = ax - cx, ty = ay - cy, tz = az - cz;
        double[] st = {0, 0, 0, Double.MAX_VALUE}; // [mvX, mvY, mvZ, minOv]

        // 3 world axes
        if (!axisTest(tx,                       fw,fh,fd, 1,0,0, 1,0,0, hx*a00+hy*a01+hz*a02, st)) return null;
        if (!axisTest(ty,                       fw,fh,fd, 0,1,0, 0,1,0, hx*a10+hy*a11+hz*a12, st)) return null;
        if (!axisTest(tz,                       fw,fh,fd, 0,0,1, 0,0,1, hx*a20+hy*a21+hz*a22, st)) return null;

        // 3 OBB local axes
        if (!axisTest(tx*m00+ty*m10+tz*m20, fw,fh,fd, m00,m10,m20, a00,a10,a20, hx, st)) return null;
        if (!axisTest(tx*m01+ty*m11+tz*m21, fw,fh,fd, m01,m11,m21, a01,a11,a21, hy, st)) return null;
        if (!axisTest(tx*m02+ty*m12+tz*m22, fw,fh,fd, m02,m12,m22, a02,a12,a22, hz, st)) return null;

        // 9 cross-product axes: e_i × u_j
        // OBB radius formula: hy*a[i][j+1 mod 3 relevant] + hz*a[i][...] derived from triple product
        if (!axisTest(tz*m10-ty*m20, fw,fh,fd,  0,  -m20,  m10,    0, a20, a10, hy*a02+hz*a01, st)) return null;
        if (!axisTest(tz*m11-ty*m21, fw,fh,fd,  0,  -m21,  m11,    0, a21, a11, hx*a02+hz*a00, st)) return null;
        if (!axisTest(tz*m12-ty*m22, fw,fh,fd,  0,  -m22,  m12,    0, a22, a12, hx*a01+hy*a00, st)) return null;
        if (!axisTest(tx*m20-tz*m00, fw,fh,fd,  m20,  0, -m00, a20,   0, a00, hy*a12+hz*a11, st)) return null;
        if (!axisTest(tx*m21-tz*m01, fw,fh,fd,  m21,  0, -m01, a21,   0, a01, hx*a12+hz*a10, st)) return null;
        if (!axisTest(tx*m22-tz*m02, fw,fh,fd,  m22,  0, -m02, a22,   0, a02, hx*a11+hy*a10, st)) return null;
        if (!axisTest(ty*m00-tx*m10, fw,fh,fd, -m10, m00,   0, a10, a00,   0, hy*a22+hz*a21, st)) return null;
        if (!axisTest(ty*m01-tx*m11, fw,fh,fd, -m11, m01,   0, a11, a01,   0, hx*a22+hz*a20, st)) return null;
        if (!axisTest(ty*m02-tx*m12, fw,fh,fd, -m12, m02,   0, a12, a02,   0, hx*a21+hy*a20, st)) return null;

        return new double[]{st[0], st[1], st[2]};
    }

    /**
     * Tests one SAT axis and updates the running minimum MTV.
     *
     * @param sep   T·L (projection of center-difference onto axis L)
     * @param nx,ny,nz   axis L (need not be unit length; cross-product axes generally aren't)
     * @param anx,any,anz |nx|,|ny|,|nz|
     * @param obbR  OBB's projected radius on L (un-normalized, consistent with sep)
     * @param st    [mvX, mvY, mvZ, minOv] — updated in-place when this axis gives the new minimum
     * @return false if separated on this axis (caller returns null immediately)
     */
    private static boolean axisTest(
            double sep, double fw, double fh, double fd,
            double nx, double ny, double nz,
            double anx, double any, double anz,
            double obbR, double[] st) {
        double lenSq = nx*nx + ny*ny + nz*nz;
        if (lenSq < 1e-10) return true; // degenerate cross product (parallel axes) — skip
        double aabbR = fw*anx + fh*any + fd*anz;
        double ov = obbR + aabbR - Math.abs(sep);
        if (ov <= EPSILON) return false;
        double invLen = 1.0 / Math.sqrt(lenSq);
        double ovN = ov * invLen; // overlap in world units along the normalised axis
        if (ovN < st[3]) {
            st[3] = ovN;
            double sg = sep >= 0 ? invLen : -invLen;
            st[0] = ovN * nx * sg;
            st[1] = ovN * ny * sg;
            st[2] = ovN * nz * sg;
        }
        return true;
    }
}
