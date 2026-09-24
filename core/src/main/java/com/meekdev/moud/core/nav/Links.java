package com.meekdev.moud.core.nav;

import java.util.Arrays;

final class Links {

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int MAX = 2048;

    private float[] verts = new float[6 * 64];
    private int[] areas = new int[64];
    private int[] dirs = new int[64];
    private int count;

    int count() {
        return count;
    }

    float[] verts() {
        return Arrays.copyOf(verts, count * 6);
    }

    int[] areas() {
        return Arrays.copyOf(areas, count);
    }

    int[] dirs() {
        return Arrays.copyOf(dirs, count);
    }

    void add(float ax, float ay, float az, float bx, float by, float bz, int area, boolean both) {
        if (count >= MAX) return;
        if (count == areas.length) {
            verts = Arrays.copyOf(verts, verts.length * 2);
            areas = Arrays.copyOf(areas, areas.length * 2);
            dirs = Arrays.copyOf(dirs, dirs.length * 2);
        }
        int v = count * 6;
        verts[v] = ax;
        verts[v + 1] = ay;
        verts[v + 2] = az;
        verts[v + 3] = bx;
        verts[v + 4] = by;
        verts[v + 5] = bz;
        areas[count] = area;
        dirs[count] = both ? 1 : 0;
        count++;
    }

    void drops(Voxels voxels, int x0, int z0, int size, float climb, float height, float drop, int area) {
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) {
                for (float from : voxels.floors(x, z)) {
                    for (int[] side : SIDES) {
                        int nx = x + side[0], nz = z + side[1];
                        float[] below = voxels.floors(nx, nz);
                        if (reaches(below, from, climb)) continue;
                        float to = highestBelow(below, from - climb, from - drop);
                        if (Float.isNaN(to) || !voxels.clear(nx, nz, to, from + height)) continue;
                        add(x + 0.5f, from, z + 0.5f, nx + 0.5f, to, nz + 0.5f, area, false);
                    }
                }
            }
        }
    }

    private static boolean reaches(float[] floors, float from, float climb) {
        for (float f : floors) if (Math.abs(f - from) <= climb) return true;
        return false;
    }

    private static float highestBelow(float[] floors, float under, float lowest) {
        float best = Float.NaN;
        for (float f : floors) {
            if (f < under && f >= lowest && (Float.isNaN(best) || f > best)) best = f;
        }
        return best;
    }
}
