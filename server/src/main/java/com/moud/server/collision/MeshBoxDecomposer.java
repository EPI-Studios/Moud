package com.moud.server.collision;

import com.moud.api.collision.OBB;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// the fallback fallback of the fallback
public class MeshBoxDecomposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MeshBoxDecomposer.class);
    private static final float TARGET_CELL_SIZE = 0.5f;
    private static final int MAX_DIVISIONS_PER_AXIS = 128;
    private static final int MAX_BOXES = 8000;
    private static final int MAX_OCCUPIED_CELLS = 5000;

    public static List<OBB> decompose(float[] vertices, int[] indices) {
        return decompose(vertices, indices, null);
    }

    public static List<OBB> decompose(float[] vertices, int[] indices, String debugLabel) {
        if (vertices == null || indices == null || vertices.length < 3 || indices.length < 3) {
            return new ArrayList<>();
        }

        Vector3 meshMin = new Vector3(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        Vector3 meshMax = new Vector3(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);

        for (int i = 0; i < vertices.length; i += 3) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];

            meshMin = new Vector3(
                    Math.min(meshMin.x, x),
                    Math.min(meshMin.y, y),
                    Math.min(meshMin.z, z)
            );
            meshMax = new Vector3(
                    Math.max(meshMax.x, x),
                    Math.max(meshMax.y, y),
                    Math.max(meshMax.z, z)
            );
        }

        Vector3 size = meshMax.subtract(meshMin);
        if (!Float.isFinite(meshMin.x) || !Float.isFinite(meshMax.x)) {
            return createFallbackBox(meshMin, meshMax);
        }

        int divX = Math.max(1, Math.min(MAX_DIVISIONS_PER_AXIS, (int) Math.ceil(size.x / TARGET_CELL_SIZE)));
        int divY = Math.max(1, Math.min(MAX_DIVISIONS_PER_AXIS, (int) Math.ceil(size.y / TARGET_CELL_SIZE)));
        int divZ = Math.max(1, Math.min(MAX_DIVISIONS_PER_AXIS, (int) Math.ceil(size.z / TARGET_CELL_SIZE)));
        float stepX = size.x / divX;
        float stepY = size.y / divY;
        float stepZ = size.z / divZ;

        boolean[] grid = new boolean[divX * divY * divZ];
        int occupied = 0;
        for (int tri = 0; tri < indices.length - 2 && occupied < MAX_OCCUPIED_CELLS; tri += 3) {
            int i0 = indices[tri] * 3;
            int i1 = indices[tri + 1] * 3;
            int i2 = indices[tri + 2] * 3;
            if (i0 + 2 >= vertices.length || i1 + 2 >= vertices.length || i2 + 2 >= vertices.length) {
                continue;
            }
            float x0 = vertices[i0];
            float y0 = vertices[i0 + 1];
            float z0 = vertices[i0 + 2];
            float x1 = vertices[i1];
            float y1 = vertices[i1 + 1];
            float z1 = vertices[i1 + 2];
            float x2 = vertices[i2];
            float y2 = vertices[i2 + 1];
            float z2 = vertices[i2 + 2];

            float triMinX = Math.min(x0, Math.min(x1, x2));
            float triMaxX = Math.max(x0, Math.max(x1, x2));
            float triMinY = Math.min(y0, Math.min(y1, y2));
            float triMaxY = Math.max(y0, Math.max(y1, y2));
            float triMinZ = Math.min(z0, Math.min(z1, z2));
            float triMaxZ = Math.max(z0, Math.max(z1, z2));

            int startX = clamp((int) Math.floor((triMinX - meshMin.x) / stepX), 0, divX - 1);
            int endX = clamp((int) Math.floor((triMaxX - meshMin.x) / stepX), 0, divX - 1);
            int startY = clamp((int) Math.floor((triMinY - meshMin.y) / stepY), 0, divY - 1);
            int endY = clamp((int) Math.floor((triMaxY - meshMin.y) / stepY), 0, divY - 1);
            int startZ = clamp((int) Math.floor((triMinZ - meshMin.z) / stepZ), 0, divZ - 1);
            int endZ = clamp((int) Math.floor((triMaxZ - meshMin.z) / stepZ), 0, divZ - 1);

            for (int ix = startX; ix <= endX && occupied < MAX_OCCUPIED_CELLS; ix++) {
                for (int iy = startY; iy <= endY && occupied < MAX_OCCUPIED_CELLS; iy++) {
                    for (int iz = startZ; iz <= endZ && occupied < MAX_OCCUPIED_CELLS; iz++) {
                        double cellMinX = meshMin.x + ix * stepX;
                        double cellMinY = meshMin.y + iy * stepY;
                        double cellMinZ = meshMin.z + iz * stepZ;
                        double cellMaxX = cellMinX + stepX;
                        double cellMaxY = cellMinY + stepY;
                        double cellMaxZ = cellMinZ + stepZ;
                        if (!triangleIntersectsAabb(
                                x0, y0, z0, x1, y1, z1, x2, y2, z2,
                                cellMinX, cellMinY, cellMinZ, cellMaxX, cellMaxY, cellMaxZ)) {
                            continue;
                        }
                        int idx = (iy * divZ + iz) * divX + ix;
                        if (!grid[idx]) {
                            grid[idx] = true;
                            occupied++;
                        }
                    }
                }
            }
        }

        if (occupied == 0) {
            return createFallbackBox(meshMin, meshMax);
        }

        List<RunZ> yzRuns = new ArrayList<>();
        for (int y = 0; y < divY; y++) {
            java.util.HashMap<String, TempRun> active = new java.util.HashMap<>();
            for (int z = 0; z < divZ; z++) {
                java.util.Set<String> seenThisZ = new java.util.HashSet<>();
                int x = 0;
                while (x < divX) {
                    int idx = (y * divZ + z) * divX + x;
                    if (grid[idx]) {
                        int x0 = x;
                        while (x + 1 < divX && grid[(y * divZ + z) * divX + (x + 1)]) {
                            x++;
                        }
                        int x1 = x;
                        String key = x0 + "|" + x1;
                        TempRun run = active.get(key);
                        if (run != null && run.lastZ == z - 1) {
                            run.lastZ = z;
                        } else {
                            if (run != null) {
                                yzRuns.add(new RunZ(run.x0, run.x1, run.startZ, run.lastZ, y));
                            }
                            active.put(key, new TempRun(x0, x1, z, z, y));
                        }
                        seenThisZ.add(key);
                    }
                    x++;
                }
                java.util.List<String> toRemove = new java.util.ArrayList<>();
                for (var entry : active.entrySet()) {
                    if (!seenThisZ.contains(entry.getKey()) && entry.getValue().lastZ < z) {
                        TempRun run = entry.getValue();
                        yzRuns.add(new RunZ(run.x0, run.x1, run.startZ, run.lastZ, y));
                        toRemove.add(entry.getKey());
                    }
                }
                for (String k : toRemove) {
                    active.remove(k);
                }
            }
            for (TempRun run : active.values()) {
                yzRuns.add(new RunZ(run.x0, run.x1, run.startZ, run.lastZ, y));
            }
        }

        if (yzRuns.isEmpty()) {
            return createFallbackBox(meshMin, meshMax);
        }

        yzRuns.sort(java.util.Comparator.comparingInt((RunZ r) -> r.y)
                .thenComparingInt(r -> r.z0)
                .thenComparingInt(r -> r.x0));

        List<RunY> mergedRuns = new ArrayList<>();
        java.util.HashMap<String, RunY> activeY = new java.util.HashMap<>();
        int currentY = yzRuns.get(0).y;
        for (RunZ run : yzRuns) {
            if (run.y != currentY) {
                activeY.values().forEach(mergedRuns::add);
                activeY.clear();
                currentY = run.y;
            }
            String key = run.x0 + "|" + run.x1 + "|" + run.z0 + "|" + run.z1;
            RunY existing = activeY.get(key);
            if (existing != null && existing.endY == run.y - 1) {
                existing.endY = run.y;
            } else {
                if (existing != null) {
                    mergedRuns.add(existing);
                }
                activeY.put(key, new RunY(run.x0, run.x1, run.z0, run.z1, run.y, run.y));
            }
        }
        activeY.values().forEach(mergedRuns::add);

        List<OBB> boxes = new ArrayList<>();
        int used = 0;
        for (RunY r : mergedRuns) {
            double cellMinX = meshMin.x + r.x0 * stepX;
            double cellMaxX = meshMin.x + (r.x1 + 1) * stepX;
            double cellMinZ = meshMin.z + r.z0 * stepZ;
            double cellMaxZ = meshMin.z + (r.z1 + 1) * stepZ;
            double cellMinY = meshMin.y + r.startY * stepY;
            double cellMaxY = meshMin.y + (r.endY + 1) * stepY;

            Vector3 center = new Vector3(
                    (float) ((cellMinX + cellMaxX) * 0.5),
                    (float) ((cellMinY + cellMaxY) * 0.5),
                    (float) ((cellMinZ + cellMaxZ) * 0.5)
            );
            Vector3 halfExtents = new Vector3(
                    (float) ((cellMaxX - cellMinX) * 0.5),
                    (float) ((cellMaxY - cellMinY) * 0.5),
                    (float) ((cellMaxZ - cellMinZ) * 0.5)
            );
            boxes.add(new OBB(center, halfExtents, Quaternion.identity()));
            used++;
            if (used >= MAX_BOXES) {
                break;
            }
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Collision voxelization {}divisions=({}, {}, {}), occupied={}, boxes={}, fallback={}",
                    debugLabel != null ? "[" + debugLabel + "] " : "",
                    divX, divY, divZ, occupied, boxes.size(), boxes.isEmpty());
        }
        return boxes.isEmpty() ? createFallbackBox(meshMin, meshMax) : boxes;
    }

    private static List<OBB> createFallbackBox(Vector3 meshMin, Vector3 meshMax) {
        List<OBB> boxes = new ArrayList<>();
        Vector3 center = meshMin.add(meshMax).multiply(0.5f);
        Vector3 halfExtents = meshMax.subtract(meshMin).multiply(0.5f);
        boxes.add(new OBB(center, halfExtents, Quaternion.identity()));
        return boxes;
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static final class TempRun {
        final int x0;
        final int x1;
        final int startZ;
        int lastZ;
        final int y;

        TempRun(int x0, int x1, int startZ, int lastZ, int y) {
            this.x0 = x0;
            this.x1 = x1;
            this.startZ = startZ;
            this.lastZ = lastZ;
            this.y = y;
        }
    }

    private static final class RunZ {
        final int x0;
        final int x1;
        final int z0;
        final int z1;
        final int y;

        RunZ(int x0, int x1, int z0, int z1, int y) {
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
            this.y = y;
        }
    }

    private static final class RunY {
        final int x0;
        final int x1;
        final int z0;
        final int z1;
        int startY;
        int endY;

        RunY(int x0, int x1, int z0, int z1, int startY, int endY) {
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
            this.startY = startY;
            this.endY = endY;
        }
    }

    private static boolean triangleIntersectsAabb(
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {

        final float EPS = 1e-6f;

        float[] v0 = {(float) x0, (float) y0, (float) z0};
        float[] v1 = {(float) x1, (float) y1, (float) z1};
        float[] v2 = {(float) x2, (float) y2, (float) z2};

        float[] c = {
                (float) ((minX + maxX) * 0.5),
                (float) ((minY + maxY) * 0.5),
                (float) ((minZ + maxZ) * 0.5)
        };
        float[] h = {
                (float) ((maxX - minX) * 0.5),
                (float) ((maxY - minY) * 0.5),
                (float) ((maxZ - minZ) * 0.5)
        };

        float[] tv0 = {v0[0] - c[0], v0[1] - c[1], v0[2] - c[2]};
        float[] tv1 = {v1[0] - c[0], v1[1] - c[1], v1[2] - c[2]};
        float[] tv2 = {v2[0] - c[0], v2[1] - c[1], v2[2] - c[2]};

        float[] e0 = {tv1[0] - tv0[0], tv1[1] - tv0[1], tv1[2] - tv0[2]};
        float[] e1 = {tv2[0] - tv1[0], tv2[1] - tv1[1], tv2[2] - tv1[2]};
        float[] e2 = {tv0[0] - tv2[0], tv0[1] - tv2[1], tv0[2] - tv2[2]};

        if (!axisTest(e0, tv0, tv1, tv2, h, EPS)) return false;
        if (!axisTest(e1, tv0, tv1, tv2, h, EPS)) return false;
        if (!axisTest(e2, tv0, tv1, tv2, h, EPS)) return false;

        if (!overlapOnAxis(tv0[0], tv1[0], tv2[0], h[0], EPS)) return false;
        if (!overlapOnAxis(tv0[1], tv1[1], tv2[1], h[1], EPS)) return false;
        if (!overlapOnAxis(tv0[2], tv1[2], tv2[2], h[2], EPS)) return false;

        float[] normal = {
                e0[1] * e1[2] - e0[2] * e1[1],
                e0[2] * e1[0] - e0[0] * e1[2],
                e0[0] * e1[1] - e0[1] * e1[0]
        };
        return planeBoxOverlap(normal, tv0, h, EPS);
    }

    private static boolean overlapOnAxis(float a, float b, float c, float half, float eps) {
        float min = Math.min(a, Math.min(b, c));
        float max = Math.max(a, Math.max(b, c));
        return !(min > half + eps || max < -half - eps);
    }

    private static boolean axisTest(float[] e, float[] v0, float[] v1, float[] v2, float[] h, float eps) {
        float ex = Math.abs(e[0]);
        float ey = Math.abs(e[1]);
        float ez = Math.abs(e[2]);

        float p0 = e[2] * v0[1] - e[1] * v0[2];
        float p1 = e[2] * v1[1] - e[1] * v1[2];
        float p2 = e[2] * v2[1] - e[1] * v2[2];
        float min = Math.min(p0, Math.min(p1, p2));
        float max = Math.max(p0, Math.max(p1, p2));
        float rad = ez * h[1] + ey * h[2];
        if (min > rad + eps || max < -rad - eps) return false;

        p0 = -e[2] * v0[0] + e[0] * v0[2];
        p1 = -e[2] * v1[0] + e[0] * v1[2];
        p2 = -e[2] * v2[0] + e[0] * v2[2];
        min = Math.min(p0, Math.min(p1, p2));
        max = Math.max(p0, Math.max(p1, p2));
        rad = ez * h[0] + ex * h[2];
        if (min > rad + eps || max < -rad - eps) return false;

        p0 = e[1] * v0[0] - e[0] * v0[1];
        p1 = e[1] * v1[0] - e[0] * v1[1];
        p2 = e[1] * v2[0] - e[0] * v2[1];
        min = Math.min(p0, Math.min(p1, p2));
        max = Math.max(p0, Math.max(p1, p2));
        rad = ey * h[0] + ex * h[1];
        return !(min > rad + eps || max < -rad - eps);
    }

    private static boolean planeBoxOverlap(float[] n, float[] v0, float[] h, float eps) {
        float[] vmin = new float[3];
        float[] vmax = new float[3];
        for (int q = 0; q < 3; q++) {
            float v = v0[q];
            if (n[q] > 0.0f) {
                vmin[q] = -h[q] - v;
                vmax[q] =  h[q] - v;
            } else {
                vmin[q] =  h[q] - v;
                vmax[q] = -h[q] - v;
            }
        }
        float dmin = n[0] * vmin[0] + n[1] * vmin[1] + n[2] * vmin[2];
        if (dmin > eps) return false;
        float dmax = n[0] * vmax[0] + n[1] * vmax[1] + n[2] * vmax[2];
        return dmax >= -eps;
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}