package com.moud.client.fabric.physics;

import net.minecraft.util.math.Box;

public record ConvexCollisionShape(
        float[] vertices,
        int[] indices,
        int layerBits,
        int maskBits,
        Box worldAabb
) implements CollisionShape {
    private static final double EPSILON = 1.0e-6;

    public static ConvexCollisionShape of(float[] vertices, int[] indices, int layerBits, int maskBits) {
        if (vertices == null || vertices.length < 9 || indices == null || indices.length < 3) {
            return null;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i + 2 < vertices.length; i += 3) {
            double x = vertices[i];
            double y = vertices[i + 1];
            double z = vertices[i + 2];
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                continue;
            }
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        if (!Double.isFinite(minX) || maxX - minX <= EPSILON || maxY - minY <= EPSILON || maxZ - minZ <= EPSILON) {
            return null;
        }
        return new ConvexCollisionShape(vertices, indices, layerBits, maskBits, new Box(minX, minY, minZ, maxX, maxY, maxZ));
    }

    @Override
    public double[] computeMtv(double ax, double ay, double az, double fw, double fh, double fd) {
        double[] best = {0.0, 0.0, 0.0, Double.MAX_VALUE};

        if (!axisTest(1.0, 0.0, 0.0, ax, ay, az, fw, fh, fd, best)) return null;
        if (!axisTest(0.0, 1.0, 0.0, ax, ay, az, fw, fh, fd, best)) return null;
        if (!axisTest(0.0, 0.0, 1.0, ax, ay, az, fw, fh, fd, best)) return null;

        for (int i = 0; i + 2 < indices.length; i += 3) {
            int ia = indices[i] * 3;
            int ib = indices[i + 1] * 3;
            int ic = indices[i + 2] * 3;
            if (ic + 2 >= vertices.length || ib + 2 >= vertices.length || ia + 2 >= vertices.length) {
                continue;
            }

            double ax0 = vertices[ia];
            double ay0 = vertices[ia + 1];
            double az0 = vertices[ia + 2];
            double bx0 = vertices[ib];
            double by0 = vertices[ib + 1];
            double bz0 = vertices[ib + 2];
            double cx0 = vertices[ic];
            double cy0 = vertices[ic + 1];
            double cz0 = vertices[ic + 2];

            double e0x = bx0 - ax0;
            double e0y = by0 - ay0;
            double e0z = bz0 - az0;
            double e1x = cx0 - ax0;
            double e1y = cy0 - ay0;
            double e1z = cz0 - az0;

            double nx = e0y * e1z - e0z * e1y;
            double ny = e0z * e1x - e0x * e1z;
            double nz = e0x * e1y - e0y * e1x;
            if (!axisTest(nx, ny, nz, ax, ay, az, fw, fh, fd, best)) return null;

            if (!edgeAxisTest(e0x, e0y, e0z, ax, ay, az, fw, fh, fd, best)) return null;
            double e2x = cx0 - bx0;
            double e2y = cy0 - by0;
            double e2z = cz0 - bz0;
            if (!edgeAxisTest(e2x, e2y, e2z, ax, ay, az, fw, fh, fd, best)) return null;
            double e3x = ax0 - cx0;
            double e3y = ay0 - cy0;
            double e3z = az0 - cz0;
            if (!edgeAxisTest(e3x, e3y, e3z, ax, ay, az, fw, fh, fd, best)) return null;
        }

        return new double[]{best[0], best[1], best[2]};
    }

    private boolean edgeAxisTest(double ex, double ey, double ez,
                                 double ax, double ay, double az,
                                 double fw, double fh, double fd,
                                 double[] best) {
        if (!axisTest(0.0, -ez, ey, ax, ay, az, fw, fh, fd, best)) return false;
        if (!axisTest(ez, 0.0, -ex, ax, ay, az, fw, fh, fd, best)) return false;
        return axisTest(-ey, ex, 0.0, ax, ay, az, fw, fh, fd, best);
    }

    private boolean axisTest(double nx, double ny, double nz,
                             double boxCx, double boxCy, double boxCz,
                             double fw, double fh, double fd,
                             double[] best) {
        double lenSq = nx * nx + ny * ny + nz * nz;
        if (lenSq < 1.0e-10) {
            return true;
        }
        double invLen = 1.0 / Math.sqrt(lenSq);
        double ux = nx * invLen;
        double uy = ny * invLen;
        double uz = nz * invLen;

        double hullMin = Double.POSITIVE_INFINITY;
        double hullMax = Double.NEGATIVE_INFINITY;
        for (int i = 0; i + 2 < vertices.length; i += 3) {
            double projection = vertices[i] * ux + vertices[i + 1] * uy + vertices[i + 2] * uz;
            hullMin = Math.min(hullMin, projection);
            hullMax = Math.max(hullMax, projection);
        }
        if (!Double.isFinite(hullMin)) {
            return true;
        }

        double boxCenter = boxCx * ux + boxCy * uy + boxCz * uz;
        double boxRadius = fw * Math.abs(ux) + fh * Math.abs(uy) + fd * Math.abs(uz);
        double boxMin = boxCenter - boxRadius;
        double boxMax = boxCenter + boxRadius;

        double overlap = Math.min(boxMax, hullMax) - Math.max(boxMin, hullMin);
        if (overlap <= EPSILON) {
            return false;
        }
        if (overlap < best[3]) {
            double hullCenter = (hullMin + hullMax) * 0.5;
            double sign = boxCenter >= hullCenter ? 1.0 : -1.0;
            best[0] = ux * overlap * sign;
            best[1] = uy * overlap * sign;
            best[2] = uz * overlap * sign;
            best[3] = overlap;
        }
        return true;
    }
}
