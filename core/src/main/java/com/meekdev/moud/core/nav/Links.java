package com.meekdev.moud.core.nav;

import com.meekdev.moud.core.math.Vector3;
import java.util.Arrays;

final class Links {

    static final int DROP = 0;
    static final int JUMP = 1;
    static final int CLIMB = 2;
    static final int CUSTOM = 3;

    private record Side(int x, int z) {}

    private static final Side[] SIDES = {new Side(1, 0), new Side(-1, 0), new Side(0, 1), new Side(0, -1)};
    private static final int MAX = 4096;
    private static final int POINTS = 6;
    private static final float ARC = 1.3f;
    private static final float LADDER_REACH = 0.6f;
    private static final float LADDER_HEADROOM = 0.5f;
    private static final float TOUCH = 0.01f;

    private final Voxels voxels;
    private final int x0;
    private final int z0;
    private final int size;
    private final float climb;
    private float[] points = new float[POINTS * 64];
    private int[] areas = new int[64];
    private int[] directions = new int[64];
    private int[] kinds = new int[64];
    private int count;

    Links(Voxels voxels, int x0, int z0, int size, float climb) {
        this.voxels = voxels;
        this.x0 = x0;
        this.z0 = z0;
        this.size = size;
        this.climb = climb;
    }

    int count() {
        return count;
    }

    float[] verts() {
        return Arrays.copyOf(points, count * POINTS);
    }

    int[] areas() {
        return Arrays.copyOf(areas, count);
    }

    int[] dirs() {
        return Arrays.copyOf(directions, count);
    }

    int[] kinds() {
        return Arrays.copyOf(kinds, count);
    }

    void add(Vector3 from, Vector3 to, int area, boolean both, int kind) {
        add((float) from.x(), (float) from.y(), (float) from.z(), (float) to.x(), (float) to.y(), (float) to.z(), area, both, kind);
    }

    void drops(float drop, int area) {
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) {
                for (float from : voxels.floors(x, z)) {
                    for (Side side : SIDES) dropBeside(x, z, from, side, drop, area);
                }
            }
        }
    }

    void jumps(int area) {
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) {
                for (float from : voxels.floors(x, z)) {
                    if (!voxels.clear(x, z, from, from + voxels.height + ARC)) continue;
                    for (Side side : SIDES) jumpAcross(x, z, from, side, area);
                }
            }
        }
    }

    void climbs(int area) {
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) {
                int bottom = -1;
                for (int layer = 0; layer <= voxels.layers; layer++) {
                    boolean climbable = layer < voxels.layers && voxels.at(x, layer, z).climbable();
                    if (climbable && bottom < 0) bottom = layer;
                    if (climbable || bottom < 0) continue;
                    ladder(x, z, voxels.base + bottom, voxels.base + layer, area);
                    bottom = -1;
                }
            }
        }
    }

    private void dropBeside(int x, int z, float from, Side side, float drop, int area) {
        int belowX = x + side.x();
        int belowZ = z + side.z();
        float[] below = voxels.floors(belowX, belowZ);
        if (reaches(below, from)) return;
        float to = highestBelow(below, from - climb, from - drop);
        if (Float.isNaN(to) || !voxels.clear(belowX, belowZ, to, from + voxels.height)) return;
        addBetweenBlocks(x, from, z, belowX, to, belowZ, area, false, DROP);
    }

    private void jumpAcross(int x, int z, float from, Side side, int area) {
        int gapX = x + side.x();
        int gapZ = z + side.z();
        if (reaches(voxels.floors(gapX, gapZ), from)) return;
        int landX = x + 2 * side.x();
        int landZ = z + 2 * side.z();
        for (float to : voxels.floors(landX, landZ)) {
            if (Math.abs(to - from) > climb) continue;
            float bottom = Math.min(from, to);
            float top = Math.max(from, to) + voxels.height + ARC;
            if (!voxels.clear(gapX, gapZ, bottom, top) || !voxels.clear(landX, landZ, to, top)) continue;
            addBetweenBlocks(x, from, z, landX, to, landZ, area, false, JUMP);
        }
    }

    private void ladder(int x, int z, float bottom, float top, int area) {
        float start = Float.NaN;
        for (float floor : voxels.floors(x, z)) {
            if (floor >= bottom - TOUCH && floor <= bottom + LADDER_REACH) start = floor;
        }
        if (Float.isNaN(start)) return;
        for (Side side : SIDES) {
            int exitX = x + side.x();
            int exitZ = z + side.z();
            for (float to : voxels.floors(exitX, exitZ)) {
                if (to <= start + climb || to > top + LADDER_REACH) continue;
                if (!voxels.clear(x, z, start, to + LADDER_HEADROOM)) continue;
                addBetweenBlocks(x, start, z, exitX, to, exitZ, area, true, CLIMB);
            }
        }
    }

    private void addBetweenBlocks(int fromX, float fromY, int fromZ, int toX, float toY, int toZ, int area, boolean both, int kind) {
        add(fromX + 0.5f, fromY, fromZ + 0.5f, toX + 0.5f, toY, toZ + 0.5f, area, both, kind);
    }

    private void add(float fromX, float fromY, float fromZ, float toX, float toY, float toZ, int area, boolean both, int kind) {
        if (count >= MAX) return;
        if (count == areas.length) grow();
        int at = count * POINTS;
        points[at] = fromX;
        points[at + 1] = fromY;
        points[at + 2] = fromZ;
        points[at + 3] = toX;
        points[at + 4] = toY;
        points[at + 5] = toZ;
        areas[count] = area;
        directions[count] = both ? 1 : 0;
        kinds[count] = kind;
        count++;
    }

    private void grow() {
        points = Arrays.copyOf(points, points.length * 2);
        areas = Arrays.copyOf(areas, areas.length * 2);
        directions = Arrays.copyOf(directions, directions.length * 2);
        kinds = Arrays.copyOf(kinds, kinds.length * 2);
    }

    private boolean reaches(float[] floors, float from) {
        for (float floor : floors) {
            if (Math.abs(floor - from) <= climb) return true;
        }
        return false;
    }

    private static float highestBelow(float[] floors, float under, float lowest) {
        float best = Float.NaN;
        for (float floor : floors) {
            boolean inRange = floor < under && floor >= lowest;
            if (inRange && (Float.isNaN(best) || floor > best)) best = floor;
        }
        return best;
    }
}
