package com.moud.core.csg;

import java.util.Objects;

public final class CsgVoxelizer {

    private static final double EPSILON = 1e-9;
    private static final float ROTATION_TOLERANCE_DEG = 1e-3f;

    private CsgVoxelizer() {
    }

    public static void forEachVoxel(VoxelDefinition def, VoxelConsumer out) {
        Objects.requireNonNull(def, "Voxel definition cannot be null");
        Objects.requireNonNull(out, "Voxel consumer cannot be null");

        int w = Math.max(1, def.width);
        int h = Math.max(1, def.height);
        int d = Math.max(1, def.depth);

        float rx = normalizeDeg(def.rotXDeg);
        float ry = normalizeDeg(def.rotYDeg);
        float rz = normalizeDeg(def.rotZDeg);

        if (!isRotated(rx, ry, rz)) {
            scanAxisAlignedBox(def.x, def.y, def.z, w, h, d, out);
            return;
        }

        scanRotatedBox(new VoxelDefinition(def.x, def.y, def.z, w, h, d, rx, ry, rz), w, h, d, out);
    }

    private static boolean isRotated(float rxDeg, float ryDeg, float rzDeg) {
        return Math.abs(rxDeg) > ROTATION_TOLERANCE_DEG
                || Math.abs(ryDeg) > ROTATION_TOLERANCE_DEG
                || Math.abs(rzDeg) > ROTATION_TOLERANCE_DEG;
    }

    private static float normalizeDeg(float deg) {
        if (!Float.isFinite(deg)) {
            return 0.0f;
        }
        float wrapped = (float) (deg % 360.0);
        if (wrapped > 180.0f) {
            wrapped -= 360.0f;
        } else if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        if (Math.abs(wrapped) < ROTATION_TOLERANCE_DEG) {
            return 0.0f;
        }
        return wrapped;
    }

    private static void scanAxisAlignedBox(int x, int y, int z, int w, int h, int d, VoxelConsumer out) {
        int maxX = x + w - 1;
        int maxY = y + h - 1;
        int maxZ = z + d - 1;

        for (int zz = z; zz <= maxZ; zz++) {
            for (int yy = y; yy <= maxY; yy++) {
                for (int xx = x; xx <= maxX; xx++) {
                    out.accept(xx, yy, zz);
                }
            }
        }
    }

    private static void scanRotatedBox(VoxelDefinition def, int w, int h, int d, VoxelConsumer out) {
        double cx = def.x + w / 2.0;
        double cy = def.y + h / 2.0;
        double cz = def.z + d / 2.0;

        double hx = w / 2.0;
        double hy = h / 2.0;
        double hz = d / 2.0;

        OrientedBox obb = OrientedBox.fromEulerAngles(cx, cy, cz, hx, hy, hz, def.rotXDeg, def.rotYDeg, def.rotZDeg);

        double worldRadiusX = obb.computeProjectedRadius(1, 0, 0);
        double worldRadiusY = obb.computeProjectedRadius(0, 1, 0);
        double worldRadiusZ = obb.computeProjectedRadius(0, 0, 1);

        int minX = (int) Math.floor(cx - worldRadiusX - 0.5);
        int maxX = (int) Math.floor(cx + worldRadiusX - 0.5);
        int minY = (int) Math.floor(cy - worldRadiusY - 0.5);
        int maxY = (int) Math.floor(cy + worldRadiusY - 0.5);
        int minZ = (int) Math.floor(cz - worldRadiusZ - 0.5);
        int maxZ = (int) Math.floor(cz + worldRadiusZ - 0.5);

        for (int z = minZ; z <= maxZ; z++) {
            double voxelCenterZ = z + 0.5;
            for (int y = minY; y <= maxY; y++) {
                double voxelCenterY = y + 0.5;
                for (int x = minX; x <= maxX; x++) {
                    double voxelCenterX = x + 0.5;

                    if (obb.intersectsVoxel(voxelCenterX, voxelCenterY, voxelCenterZ)) {
                        out.accept(x, y, z);
                    }
                }
            }
        }
    }

    @FunctionalInterface
    public interface VoxelConsumer {
        void accept(int x, int y, int z);
    }

    public record VoxelDefinition(
            int x, int y, int z,
            int width, int height, int depth,
            float rotXDeg, float rotYDeg, float rotZDeg
    ) {
        public boolean isRotated() {
            return CsgVoxelizer.isRotated(normalizeDeg(rotXDeg), normalizeDeg(rotYDeg), normalizeDeg(rotZDeg));
        }
    }

    private record OrientedBox(double cx, double cy, double cz, double hx, double hy, double hz, double m00, double m01,
                               double m02, double m10, double m11, double m12, double m20, double m21, double m22) {

        static OrientedBox fromEulerAngles(double cx, double cy, double cz,
                                               double hx, double hy, double hz,
                                               float rxDeg, float ryDeg, float rzDeg) {
                double rx = Math.toRadians(rxDeg);
                double ry = Math.toRadians(ryDeg);
                double rz = Math.toRadians(rzDeg);

                double cX = Math.cos(rx), sX = Math.sin(rx);
                double cY = Math.cos(ry), sY = Math.sin(ry);
                double cZ = Math.cos(rz), sZ = Math.sin(rz);

                double m00 = cZ * cY;
                double m10 = sZ * cY;
                double m20 = -sY;

                double m01 = cZ * sY * sX - sZ * cX;
                double m11 = sZ * sY * sX + cZ * cX;
                double m21 = cY * sX;

                double m02 = cZ * sY * cX + sZ * sX;
                double m12 = sZ * sY * cX - cZ * sX;
                double m22 = cY * cX;

                return new OrientedBox(cx, cy, cz, hx, hy, hz,
                        m00, m01, m02, m10, m11, m12, m20, m21, m22);
            }

            double computeProjectedRadius(double dirX, double dirY, double dirZ) {
                double rX = Math.abs((m00 * dirX + m10 * dirY + m20 * dirZ) * hx);
                double rY = Math.abs((m01 * dirX + m11 * dirY + m21 * dirZ) * hy);
                double rZ = Math.abs((m02 * dirX + m12 * dirY + m22 * dirZ) * hz);
                return rX + rY + rZ;
            }

            boolean intersectsVoxel(double vx, double vy, double vz) {
                // Separating Axis Theorem (SAT) for OBB vs axis-aligned unit voxel cube.
                // Cube is centered at (vx,vy,vz) with half-extents (0.5,0.5,0.5).
                //
                // Reference: Christer Ericson, "Real-Time Collision Detection" (OBB vs OBB); specialized for AABB.
                final double ex = hx;
                final double ey = hy;
                final double ez = hz;
                final double fx = 0.5;
                final double fy = 0.5;
                final double fz = 0.5;

                double txw = vx - cx;
                double tyw = vy - cy;
                double tzw = vz - cz;

                // Oriented box axes (world): u0=(m00,m10,m20), u1=(m01,m11,m21), u2=(m02,m12,m22)
                // R[i][j] = dot(u_i, worldAxis_j) == component of u_i on that axis.
                double r00 = m00, r01 = m10, r02 = m20;
                double r10 = m01, r11 = m11, r12 = m21;
                double r20 = m02, r21 = m12, r22 = m22;

                double ar00 = Math.abs(r00) + EPSILON, ar01 = Math.abs(r01) + EPSILON, ar02 = Math.abs(r02) + EPSILON;
                double ar10 = Math.abs(r10) + EPSILON, ar11 = Math.abs(r11) + EPSILON, ar12 = Math.abs(r12) + EPSILON;
                double ar20 = Math.abs(r20) + EPSILON, ar21 = Math.abs(r21) + EPSILON, ar22 = Math.abs(r22) + EPSILON;

                // t in OBB frame (dot with axes).
                double t0 = r00 * txw + r01 * tyw + r02 * tzw;
                double t1 = r10 * txw + r11 * tyw + r12 * tzw;
                double t2 = r20 * txw + r21 * tyw + r22 * tzw;

                // 1) Test axes L = u0, u1, u2
                double rb0 = fx * ar00 + fy * ar01 + fz * ar02;
                if (Math.abs(t0) > ex + rb0) return false;
                double rb1 = fx * ar10 + fy * ar11 + fz * ar12;
                if (Math.abs(t1) > ey + rb1) return false;
                double rb2 = fx * ar20 + fy * ar21 + fz * ar22;
                if (Math.abs(t2) > ez + rb2) return false;

                // 2) Test axes L = world X, Y, Z
                double raX = ex * ar00 + ey * ar10 + ez * ar20;
                if (Math.abs(txw) > fx + raX) return false;
                double raY = ex * ar01 + ey * ar11 + ez * ar21;
                if (Math.abs(tyw) > fy + raY) return false;
                double raZ = ex * ar02 + ey * ar12 + ez * ar22;
                if (Math.abs(tzw) > fz + raZ) return false;

                // 3) Test axis L = u_i x worldAxis_j (9 tests)
                // i=0
                double ra, rb, t;
                // u0 x X
                ra = ey * ar20 + ez * ar10;
                rb = fy * ar02 + fz * ar01;
                t = Math.abs(t2 * r10 - t1 * r20);
                if (t > ra + rb) return false;
                // u0 x Y
                ra = ey * ar21 + ez * ar11;
                rb = fx * ar02 + fz * ar00;
                t = Math.abs(t2 * r11 - t1 * r21);
                if (t > ra + rb) return false;
                // u0 x Z
                ra = ey * ar22 + ez * ar12;
                rb = fx * ar01 + fy * ar00;
                t = Math.abs(t2 * r12 - t1 * r22);
                if (t > ra + rb) return false;

                // i=1
                // u1 x X
                ra = ex * ar20 + ez * ar00;
                rb = fy * ar12 + fz * ar11;
                t = Math.abs(t0 * r20 - t2 * r00);
                if (t > ra + rb) return false;
                // u1 x Y
                ra = ex * ar21 + ez * ar01;
                rb = fx * ar12 + fz * ar10;
                t = Math.abs(t0 * r21 - t2 * r01);
                if (t > ra + rb) return false;
                // u1 x Z
                ra = ex * ar22 + ez * ar02;
                rb = fx * ar11 + fy * ar10;
                t = Math.abs(t0 * r22 - t2 * r02);
                if (t > ra + rb) return false;

                // i=2
                // u2 x X
                ra = ex * ar10 + ey * ar00;
                rb = fy * ar22 + fz * ar21;
                t = Math.abs(t1 * r00 - t0 * r10);
                if (t > ra + rb) return false;
                // u2 x Y
                ra = ex * ar11 + ey * ar01;
                rb = fx * ar22 + fz * ar20;
                t = Math.abs(t1 * r01 - t0 * r11);
                if (t > ra + rb) return false;
                // u2 x Z
                ra = ex * ar12 + ey * ar02;
                rb = fx * ar21 + fy * ar20;
                t = Math.abs(t1 * r02 - t0 * r12);
                if (t > ra + rb) return false;

                return true;
            }

            private boolean containsPoint(double px, double py, double pz) {
                double dx = px - cx;
                double dy = py - cy;
                double dz = pz - cz;

                double localX = m00 * dx + m10 * dy + m20 * dz;
                double localY = m01 * dx + m11 * dy + m21 * dz;
                double localZ = m02 * dx + m12 * dy + m22 * dz;

                return Math.abs(localX) <= hx + EPSILON &&
                        Math.abs(localY) <= hy + EPSILON &&
                        Math.abs(localZ) <= hz + EPSILON;
            }
        }
}
