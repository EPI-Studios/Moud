package com.moud.core.csg;

import java.util.Objects;

public final class CsgVoxelizer {

    private static final double EPSILON = 1e-9;
    private static final float ROTATION_TOLERANCE_DEG = 1e-3f;

    private static final double[][] SAMPLING_OFFSETS = {
            {0, 0, 0},
            {0.5, 0, 0}, {-0.5, 0, 0}, {0, 0.5, 0}, {0, -0.5, 0}, {0, 0, 0.5}, {0, 0, -0.5},
            {0.5, 0.5, 0}, {0.5, -0.5, 0}, {-0.5, 0.5, 0}, {-0.5, -0.5, 0},
            {0.5, 0, 0.5}, {0.5, 0, -0.5}, {-0.5, 0, 0.5}, {-0.5, 0, -0.5},
            {0, 0.5, 0.5}, {0, 0.5, -0.5}, {0, -0.5, 0.5}, {0, -0.5, -0.5}
    };

    private CsgVoxelizer() {
    }

    public static void forEachVoxel(VoxelDefinition def, VoxelConsumer out) {
        Objects.requireNonNull(def, "Voxel definition cannot be null");
        Objects.requireNonNull(out, "Voxel consumer cannot be null");

        int w = Math.max(1, def.width);
        int h = Math.max(1, def.height);
        int d = Math.max(1, def.depth);

        if (!def.isRotated()) {
            scanAxisAlignedBox(def.x, def.y, def.z, w, h, d, out);
            return;
        }

        scanRotatedBox(def, w, h, d, out);
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
            return Math.abs(rotXDeg) > ROTATION_TOLERANCE_DEG ||
                    Math.abs(rotYDeg) > ROTATION_TOLERANCE_DEG ||
                    Math.abs(rotZDeg) > ROTATION_TOLERANCE_DEG;
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
                for (double[] offset : SAMPLING_OFFSETS) {
                    if (containsPoint(vx + offset[0], vy + offset[1], vz + offset[2])) {
                        return true;
                    }
                }
                return false;
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