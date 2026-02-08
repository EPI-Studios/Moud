package com.moud.client.primitives;

import net.minecraft.util.math.Box;

import java.util.*;

public final class PrimitiveVoxelizer {

    private static final float TARGET_CELL_SIZE = 0.25f;
    private static final int MAX_DIVISIONS = 64;
    private static final int MAX_OCCUPIED_CELLS = 2000;
    private static final int MAX_RESULT_BOXES = 1000;

    private PrimitiveVoxelizer() {}

    public static List<Box> voxelize(List<PrimitiveGeometry.Triangle> triangles, Box bounds) {
        double sizeX = bounds.maxX - bounds.minX;
        double sizeY = bounds.maxY - bounds.minY;
        double sizeZ = bounds.maxZ - bounds.minZ;

        int divX = clamp((int) Math.ceil(sizeX / TARGET_CELL_SIZE), 1, MAX_DIVISIONS);
        int divY = clamp((int) Math.ceil(sizeY / TARGET_CELL_SIZE), 1, MAX_DIVISIONS);
        int divZ = clamp((int) Math.ceil(sizeZ / TARGET_CELL_SIZE), 1, MAX_DIVISIONS);

        double stepX = sizeX / divX;
        double stepY = sizeY / divY;
        double stepZ = sizeZ / divZ;

        Set<Integer> occupied = new HashSet<>();

        for (PrimitiveGeometry.Triangle tri : triangles) {
            if (occupied.size() >= MAX_OCCUPIED_CELLS) break;

            int startX = getGridIndex(Math.min(tri.v0().x, Math.min(tri.v1().x, tri.v2().x)), bounds.minX, stepX, divX);
            int endX = getGridIndex(Math.max(tri.v0().x, Math.max(tri.v1().x, tri.v2().x)), bounds.minX, stepX, divX);
            int startY = getGridIndex(Math.min(tri.v0().y, Math.min(tri.v1().y, tri.v2().y)), bounds.minY, stepY, divY);
            int endY = getGridIndex(Math.max(tri.v0().y, Math.max(tri.v1().y, tri.v2().y)), bounds.minY, stepY, divY);
            int startZ = getGridIndex(Math.min(tri.v0().z, Math.min(tri.v1().z, tri.v2().z)), bounds.minZ, stepZ, divZ);
            int endZ = getGridIndex(Math.max(tri.v0().z, Math.max(tri.v1().z, tri.v2().z)), bounds.minZ, stepZ, divZ);

            for (int x = startX; x <= endX; x++) {
                for (int y = startY; y <= endY; y++) {
                    for (int z = startZ; z <= endZ; z++) {
                        if (occupied.size() >= MAX_OCCUPIED_CELLS) break;

                        Box cell = new Box(
                                bounds.minX + x * stepX, bounds.minY + y * stepY, bounds.minZ + z * stepZ,
                                bounds.minX + (x + 1) * stepX, bounds.minY + (y + 1) * stepY, bounds.minZ + (z + 1) * stepZ
                        );

                        if (triangleIntersectsBox(tri, cell)) {
                            occupied.add(pack(x, y, z));
                        }
                    }
                }
            }
        }

        if (occupied.isEmpty()) return List.of();

        return mergeCells(occupied, divX, divY, divZ, bounds, stepX, stepY, stepZ);
    }

    private static List<Box> mergeCells(Set<Integer> occupied, int divX, int divY, int divZ,
                                        Box globalBounds, double sx, double sy, double sz) {
        List<Box> boxes = new ArrayList<>();
        boolean[] grid = new boolean[divX * divY * divZ];
        boolean[] visited = new boolean[divX * divY * divZ];

        for (int key : occupied) {
            int x = (key >> 14) & 0x3F;
            int y = (key >> 7) & 0x7F;
            int z = key & 0x7F;
            if (x < divX && y < divY && z < divZ) {
                grid[(y * divZ + z) * divX + x] = true;
            }
        }

        for (int y = 0; y < divY && boxes.size() < MAX_RESULT_BOXES; y++) {
            for (int z = 0; z < divZ && boxes.size() < MAX_RESULT_BOXES; z++) {
                for (int x = 0; x < divX && boxes.size() < MAX_RESULT_BOXES; x++) {
                    int idx = (y * divZ + z) * divX + x;
                    if (!grid[idx] || visited[idx]) continue;

                    int endX = x;
                    while (endX + 1 < divX) {
                        int next = (y * divZ + z) * divX + (endX + 1);
                        if (grid[next] && !visited[next]) endX++;
                        else break;
                    }

                    int endZ = z;
                    boolean canExpandZ = true;
                    while (endZ + 1 < divZ && canExpandZ) {
                        for (int tx = x; tx <= endX; tx++) {
                            int check = (y * divZ + (endZ + 1)) * divX + tx;
                            if (!grid[check] || visited[check]) {
                                canExpandZ = false;
                                break;
                            }
                        }
                        if (canExpandZ) endZ++;
                    }

                    int endY = y;
                    boolean canExpandY = true;
                    while (endY + 1 < divY && canExpandY) {
                        for (int tz = z; tz <= endZ; tz++) {
                            for (int tx = x; tx <= endX; tx++) {
                                int check = ((endY + 1) * divZ + tz) * divX + tx;
                                if (!grid[check] || visited[check]) {
                                    canExpandY = false;
                                    break;
                                }
                            }
                        }
                        if (canExpandY) endY++;
                    }

                    for (int vy = y; vy <= endY; vy++) {
                        for (int vz = z; vz <= endZ; vz++) {
                            for (int vx = x; vx <= endX; vx++) {
                                visited[(vy * divZ + vz) * divX + vx] = true;
                            }
                        }
                    }

                    boxes.add(new Box(
                            globalBounds.minX + x * sx, globalBounds.minY + y * sy, globalBounds.minZ + z * sz,
                            globalBounds.minX + (endX + 1) * sx, globalBounds.minY + (endY + 1) * sy, globalBounds.minZ + (endZ + 1) * sz
                    ));
                }
            }
        }
        return boxes;
    }
    private static boolean triangleIntersectsBox(PrimitiveGeometry.Triangle tri, Box box) {
        double boxCenterX = (box.minX + box.maxX) * 0.5;
        double boxCenterY = (box.minY + box.maxY) * 0.5;
        double boxCenterZ = (box.minZ + box.maxZ) * 0.5;

        double extX = (box.maxX - box.minX) * 0.5;
        double extY = (box.maxY - box.minY) * 0.5;
        double extZ = (box.maxZ - box.minZ) * 0.5;

        double v0x = tri.v0().x - boxCenterX, v0y = tri.v0().y - boxCenterY, v0z = tri.v0().z - boxCenterZ;
        double v1x = tri.v1().x - boxCenterX, v1y = tri.v1().y - boxCenterY, v1z = tri.v1().z - boxCenterZ;
        double v2x = tri.v2().x - boxCenterX, v2y = tri.v2().y - boxCenterY, v2z = tri.v2().z - boxCenterZ;

        if (Math.min(v0x, Math.min(v1x, v2x)) > extX || Math.max(v0x, Math.max(v1x, v2x)) < -extX) return false;
        if (Math.min(v0y, Math.min(v1y, v2y)) > extY || Math.max(v0y, Math.max(v1y, v2y)) < -extY) return false;
        if (Math.min(v0z, Math.min(v1z, v2z)) > extZ || Math.max(v0z, Math.max(v1z, v2z)) < -extZ) return false;

        double e0x = v1x - v0x, e0y = v1y - v0y, e0z = v1z - v0z;
        double e1x = v2x - v1x, e1y = v2y - v1y, e1z = v2z - v1z;

        double nx = e0y * e1z - e0z * e1y;
        double ny = e0z * e1x - e0x * e1z;
        double nz = e0x * e1y - e0y * e1x;

        if (planeBoxOverlap(nx, ny, nz, v0x, v0y, v0z, extX, extY, extZ)) return false;


        // edge 0
        if (axisTest(0, -e0z, e0y, v0y, v0z, v1y, v1z, v2y, v2z, extY, extZ)) return false;
        if (axisTest(0, -e1z, e1y, v0y, v0z, v1y, v1z, v2y, v2z, extY, extZ)) return false;
        if (axisTest(0, -(v0z-v2z), (v0y-v2y), v0y, v0z, v1y, v1z, v2y, v2z, extY, extZ)) return false;

        // edge 1
        if (axisTest(e0z, 0, -e0x, v0z, v0x, v1z, v1x, v2z, v2x, extZ, extX)) return false;
        if (axisTest(e1z, 0, -e1x, v0z, v0x, v1z, v1x, v2z, v2x, extZ, extX)) return false;
        if (axisTest((v0z-v2z), 0, -(v0x-v2x), v0z, v0x, v1z, v1x, v2z, v2x, extZ, extX)) return false;

        // edge 2
        if (axisTest(-e0y, e0x, 0, v0x, v0y, v1x, v1y, v2x, v2y, extX, extY)) return false;
        if (axisTest(-e1y, e1x, 0, v0x, v0y, v1x, v1y, v2x, v2y, extX, extY)) return false;
        if (axisTest(-(v0y-v2y), (v0x-v2x), 0, v0x, v0y, v1x, v1y, v2x, v2y, extX, extY)) return false;

        return true;
    }

    private static boolean axisTest(double a1, double a2, double a3,
                                    double v0u, double v0v,
                                    double v1u, double v1v,
                                    double v2u, double v2v,
                                    double extU, double extV) {
        double p0 = a2 * v0u + a3 * v0v;
        double p1 = a2 * v1u + a3 * v1v;
        double p2 = a2 * v2u + a3 * v2v;
        double r = Math.abs(a2) * extU + Math.abs(a3) * extV;
        return Math.min(p0, Math.min(p1, p2)) > r || Math.max(p0, Math.max(p1, p2)) < -r;
    }

    private static boolean planeBoxOverlap(double nx, double ny, double nz,
                                           double vx, double vy, double vz,
                                           double ex, double ey, double ez) {
        double min, max;
        double vMinX = (nx > 0) ? -ex : ex;
        double vMinY = (ny > 0) ? -ey : ey;
        double vMinZ = (nz > 0) ? -ez : ez;

        double vMaxX = (nx > 0) ? ex : -ex;
        double vMaxY = (ny > 0) ? ey : -ey;
        double vMaxZ = (nz > 0) ? ez : -ez;
        double dotMin = nx * (vMinX - vx) + ny * (vMinY - vy) + nz * (vMinZ - vz);
        double dotMax = nx * (vMaxX - vx) + ny * (vMaxY - vy) + nz * (vMaxZ - vz);

        return dotMin > 0 || dotMax < 0;
    }

    private static int getGridIndex(double val, double min, double step, int max) {
        return clamp((int) Math.floor((val - min) / step), 0, max - 1);
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(val, max));
    }

    private static int pack(int x, int y, int z) {
        return (x << 14) | (y << 7) | z;
    }
}