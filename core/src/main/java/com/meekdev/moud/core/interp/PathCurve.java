package com.meekdev.moud.core.interp;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import java.util.List;

public final class PathCurve {

    private static final int STEPS = 24;

    private final List<CFrame> points;
    private final boolean closed;
    private final double[] lengths;
    private final double total;

    public PathCurve(List<CFrame> points, boolean closed) {
        if (points.isEmpty()) throw new IllegalArgumentException("a path needs at least one point");
        this.points = List.copyOf(points);
        this.closed = closed && points.size() > 2;
        int segments = segments();
        lengths = new double[segments * STEPS + 1];
        Vector3 previous = position(0, 0);
        double sum = 0;
        for (int n = 1; n <= segments * STEPS; n++) {
            int segment = (n - 1) / STEPS;
            double local = (n - segment * STEPS) / (double) STEPS;
            Vector3 at = position(segment, local);
            sum += at.distance(previous);
            lengths[n] = sum;
            previous = at;
        }
        total = sum;
    }

    public double length() {
        return total;
    }

    public CFrame sample(double alpha) {
        if (points.size() == 1) return points.getFirst();
        double target = Math.clamp(alpha, 0, 1) * total;
        int index = 0;
        if (total > 0) {
            int low = 0;
            int high = lengths.length - 1;
            while (low < high - 1) {
                int middle = (low + high) >>> 1;
                if (lengths[middle] <= target) low = middle;
                else high = middle;
            }
            index = low;
        }
        double span = index + 1 < lengths.length ? lengths[index + 1] - lengths[index] : 0;
        double inStep = span > 1e-9 ? (target - lengths[index]) / span : 0;
        double along = total > 0 ? (index + inStep) / STEPS : alpha * segments();
        int segment = Math.min((int) along, segments() - 1);
        double local = along - segment;
        Vector3 position = position(segment, local);
        Quat rotation = point(segment).rotation().slerp(point(segment + 1).rotation(), local);
        return new CFrame(position, rotation);
    }

    public Vector3 tangent(double alpha) {
        double step = 1e-3;
        Vector3 ahead = sample(Math.min(1, alpha + step)).position();
        Vector3 behind = sample(Math.max(0, alpha - step)).position();
        Vector3 way = ahead.sub(behind);
        return way.lengthSq() < 1e-12 ? new Vector3(0, 0, -1) : way.normalize();
    }

    private int segments() {
        return Math.max(1, closed ? points.size() : points.size() - 1);
    }

    private CFrame point(int index) {
        int size = points.size();
        if (closed) return points.get(Math.floorMod(index, size));
        return points.get(Math.clamp(index, 0, size - 1));
    }

    private Vector3 position(int segment, double t) {
        Vector3 p0 = point(segment - 1).position();
        Vector3 p1 = point(segment).position();
        Vector3 p2 = point(segment + 1).position();
        Vector3 p3 = point(segment + 2).position();
        double t2 = t * t;
        double t3 = t2 * t;
        return p1.mul(2)
                .add(p2.sub(p0).mul(t))
                .add(p0.mul(2).sub(p1.mul(5)).add(p2.mul(4)).sub(p3).mul(t2))
                .add(p1.mul(3).sub(p0).sub(p2.mul(3)).add(p3).mul(t3))
                .mul(0.5);
    }
}
