package com.meekdev.moud.core.nav;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import java.util.Arrays;

final class Geometry {

    private static final int[][] FACES = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};

    private float[] v = new float[3 * 4096];
    private int[] f = new int[3 * 4096];
    private int vn;
    private int fn;

    int triangles() {
        return fn / 3;
    }

    float[] vertices() {
        return Arrays.copyOf(v, vn);
    }

    int[] indices() {
        return Arrays.copyOf(f, fn);
    }

    void box(CFrame frame, Vector3 size) {
        Vector3 centre = frame.position();
        Vector3[] corners = Boxes.boxCorners(frame, size);
        int[] vertices = new int[corners.length];
        for (int i = 0; i < corners.length; i++) vertices[i] = vertex(corners[i].x(), corners[i].y(), corners[i].z());
        for (int[] face : FACES) {
            outward(centre, corners, vertices, face[0], face[1], face[2]);
            outward(centre, corners, vertices, face[0], face[2], face[3]);
        }
    }

    private void outward(Vector3 centre, Vector3[] corners, int[] vertices, int i, int j, int k) {
        Vector3 normal = corners[j].sub(corners[i]).cross(corners[k].sub(corners[i]));
        Vector3 middle = corners[i].add(corners[j]).add(corners[k]).mul(1.0 / 3);
        if (normal.dot(middle.sub(centre)) >= 0) {
            face(vertices[i], vertices[j], vertices[k]);
        } else {
            face(vertices[i], vertices[k], vertices[j]);
        }
    }

    private int vertex(double x, double y, double z) {
        if (vn + 3 > v.length) v = Arrays.copyOf(v, v.length * 2);
        v[vn++] = (float) x;
        v[vn++] = (float) y;
        v[vn++] = (float) z;
        return vn / 3 - 1;
    }

    private void face(int a, int b, int c) {
        if (fn + 3 > f.length) f = Arrays.copyOf(f, f.length * 2);
        f[fn++] = a;
        f[fn++] = b;
        f[fn++] = c;
    }
}
