package com.meekdev.moud.mod.client.editor.viewport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ScreenShapes {

    private ScreenShapes() {}

    static List<float[]> hull(List<float[]> points) {
        List<float[]> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.<float[]>comparingDouble(p -> p[0]).thenComparingDouble(p -> p[1]));
        int count = sorted.size();
        if (count < 3) return sorted;
        float[][] chain = new float[count * 2][];
        int size = 0;
        for (float[] point : sorted) {
            while (size >= 2 && cross(chain[size - 2], chain[size - 1], point) <= 0) size--;
            chain[size++] = point;
        }
        int lower = size + 1;
        for (int n = count - 2; n >= 0; n--) {
            float[] point = sorted.get(n);
            while (size >= lower && cross(chain[size - 2], chain[size - 1], point) <= 0) size--;
            chain[size++] = point;
        }
        List<float[]> hull = new ArrayList<>(size - 1);
        for (int n = 0; n < size - 1; n++) hull.add(chain[n]);
        return hull;
    }

    static boolean overlapsRect(List<float[]> polygon, float x0, float y0, float x1, float y1) {
        if (polygon.isEmpty()) return false;
        float[][] rect = {{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}};
        if (separated(polygon, rect, 1.0f, 0.0f) || separated(polygon, rect, 0.0f, 1.0f)) return false;
        for (int n = 0; n < polygon.size(); n++) {
            float[] a = polygon.get(n);
            float[] b = polygon.get((n + 1) % polygon.size());
            float axisX = -(b[1] - a[1]);
            float axisY = b[0] - a[0];
            if (axisX == 0 && axisY == 0) continue;
            if (separated(polygon, rect, axisX, axisY)) return false;
        }
        return true;
    }

    private static boolean separated(List<float[]> polygon, float[][] rect, float axisX, float axisY) {
        float polyMin = Float.MAX_VALUE;
        float polyMax = -Float.MAX_VALUE;
        for (float[] p : polygon) {
            float d = p[0] * axisX + p[1] * axisY;
            polyMin = Math.min(polyMin, d);
            polyMax = Math.max(polyMax, d);
        }
        float rectMin = Float.MAX_VALUE;
        float rectMax = -Float.MAX_VALUE;
        for (float[] p : rect) {
            float d = p[0] * axisX + p[1] * axisY;
            rectMin = Math.min(rectMin, d);
            rectMax = Math.max(rectMax, d);
        }
        return polyMax < rectMin || rectMax < polyMin;
    }

    private static float cross(float[] o, float[] a, float[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }
}
