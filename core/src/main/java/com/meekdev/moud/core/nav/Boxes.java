package com.meekdev.moud.core.nav;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.nav.NavMeshes.World;
import java.util.Arrays;
import org.recast4j.recast.AreaModification;
import org.recast4j.recast.ConvexVolume;

final class Boxes {

    private static final float VOLUME_HEIGHT_PAD = 0.25f;

    private Boxes() {}

    static Vector3[] boxCorners(CFrame frame, Vector3 size) {
        Vector3 half = size.mul(0.5);
        Vector3[] corners = new Vector3[8];
        int corner = 0;
        for (double z : new double[] {-half.z(), half.z()}) {
            for (double y : new double[] {-half.y(), half.y()}) {
                for (double x : new double[] {-half.x(), half.x()}) {
                    corners[corner++] = frame.pointToWorld(new Vector3(x, y, z));
                }
            }
        }
        return corners;
    }

    static ConvexVolume labelledVolume(World.Box box, int area) {
        Vector3[] corners = boxCorners(box.frame(), box.size());
        float[] xs = new float[corners.length];
        float[] zs = new float[corners.length];
        double lowest = Double.MAX_VALUE;
        double highest = -Double.MAX_VALUE;
        for (int i = 0; i < corners.length; i++) {
            xs[i] = (float) corners[i].x();
            zs[i] = (float) corners[i].z();
            lowest = Math.min(lowest, corners[i].y());
            highest = Math.max(highest, corners[i].y());
        }
        ConvexVolume volume = new ConvexVolume();
        volume.verts = hull(xs, zs);
        volume.hmin = (float) lowest - VOLUME_HEIGHT_PAD;
        volume.hmax = (float) highest + VOLUME_HEIGHT_PAD;
        volume.areaMod = new AreaModification(area);
        return volume;
    }

    private static float[] hull(float[] xs, float[] zs) {
        int count = xs.length;
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> xs[a] != xs[b] ? Float.compare(xs[a], xs[b]) : Float.compare(zs[a], zs[b]));

        int[] chain = new int[2 * count];
        int size = 0;
        for (int i = 0; i < count; i++) size = pushTurningLeft(chain, size, 0, xs, zs, order[i]);
        size--;
        int upperStart = size;
        for (int i = count - 1; i >= 0; i--) size = pushTurningLeft(chain, size, upperStart, xs, zs, order[i]);
        size--;

        float[] verts = new float[size * 3];
        for (int i = 0; i < size; i++) {
            verts[i * 3] = xs[chain[i]];
            verts[i * 3 + 2] = zs[chain[i]];
        }
        return verts;
    }

    private static int pushTurningLeft(int[] chain, int size, int start, float[] xs, float[] zs, int point) {
        while (size >= start + 2 && cross(xs, zs, chain[size - 2], chain[size - 1], point) <= 0) size--;
        chain[size] = point;
        return size + 1;
    }

    private static float cross(float[] xs, float[] zs, int origin, int a, int b) {
        return (xs[a] - xs[origin]) * (zs[b] - zs[origin]) - (zs[a] - zs[origin]) * (xs[b] - xs[origin]);
    }
}
