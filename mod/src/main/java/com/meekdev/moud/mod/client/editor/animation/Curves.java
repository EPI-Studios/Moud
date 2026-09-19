package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.math.Vector3;
import java.util.List;

public final class Curves {

    private static final int SOLVE_STEPS = 24;
    private static final double THIRD = 1.0 / 3.0;

    private Curves() {}

    public static Vector3 sample(List<AnimKey> keys, double time, Vector3 rest) {
        if (keys.isEmpty()) return rest;
        AnimKey first = keys.getFirst();
        if (time <= first.time()) return first.value();
        AnimKey last = keys.getLast();
        if (time >= last.time()) return last.value();
        int index = segment(keys, time);
        AnimKey from = keys.get(index);
        AnimKey to = keys.get(index + 1);
        double span = to.time() - from.time();
        if (span <= AnimClip.EPSILON) return to.value();
        double alpha = (time - from.time()) / span;
        return switch (from.interp()) {
            case STEP -> from.value();
            case LINEAR -> from.value().lerp(to.value(), alpha);
            case SMOOTH -> smooth(keys, index, time);
            case BEZIER -> bezier(from, to, time);
        };
    }

    public static double component(Vector3 value, int axis) {
        return switch (axis) {
            case 0 -> value.x();
            case 1 -> value.y();
            default -> value.z();
        };
    }

    public static Vector3 withComponent(Vector3 value, int axis, double changed) {
        return switch (axis) {
            case 0 -> new Vector3(changed, value.y(), value.z());
            case 1 -> new Vector3(value.x(), changed, value.z());
            default -> new Vector3(value.x(), value.y(), changed);
        };
    }

    public static AnimKey.Handle outHandle(AnimKey from, AnimKey to) {
        if (from.out() != null) return from.out();
        return new AnimKey.Handle((to.time() - from.time()) * THIRD, Vector3.ZERO);
    }

    public static AnimKey.Handle inHandle(AnimKey from, AnimKey to) {
        if (to.in() != null) return to.in();
        return new AnimKey.Handle(-(to.time() - from.time()) * THIRD, Vector3.ZERO);
    }

    private static int segment(List<AnimKey> keys, double time) {
        int low = 0;
        int high = keys.size() - 2;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (keys.get(middle).time() <= time) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    private static Vector3 smooth(List<AnimKey> keys, int index, double time) {
        AnimKey p1 = keys.get(index);
        AnimKey p2 = keys.get(index + 1);
        AnimKey p0 = index > 0 ? keys.get(index - 1) : p1;
        AnimKey p3 = index + 2 < keys.size() ? keys.get(index + 2) : p2;
        double span = p2.time() - p1.time();
        double s = (time - p1.time()) / span;
        Vector3 m1 = tangent(p0, p2);
        Vector3 m2 = tangent(p1, p3);
        double s2 = s * s;
        double s3 = s2 * s;
        double h00 = 2 * s3 - 3 * s2 + 1;
        double h10 = s3 - 2 * s2 + s;
        double h01 = -2 * s3 + 3 * s2;
        double h11 = s3 - s2;
        return p1.value().mul(h00).add(m1.mul(h10 * span)).add(p2.value().mul(h01)).add(m2.mul(h11 * span));
    }

    private static Vector3 tangent(AnimKey before, AnimKey after) {
        double span = after.time() - before.time();
        if (span <= AnimClip.EPSILON) return Vector3.ZERO;
        return after.value().sub(before.value()).mul(1.0 / span);
    }

    private static Vector3 bezier(AnimKey from, AnimKey to, double time) {
        double span = to.time() - from.time();
        AnimKey.Handle out = outHandle(from, to);
        AnimKey.Handle in = inHandle(from, to);
        double x1 = from.time() + Math.clamp(out.dt(), 0, span);
        double x2 = to.time() + Math.clamp(in.dt(), -span, 0);
        double s = solve(from.time(), x1, x2, to.time(), time);
        Vector3 c1 = from.value().add(out.dv());
        Vector3 c2 = to.value().add(in.dv());
        return new Vector3(
                cubic(from.value().x(), c1.x(), c2.x(), to.value().x(), s),
                cubic(from.value().y(), c1.y(), c2.y(), to.value().y(), s),
                cubic(from.value().z(), c1.z(), c2.z(), to.value().z(), s));
    }

    static double cubic(double p0, double p1, double p2, double p3, double s) {
        double u = 1 - s;
        return u * u * u * p0 + 3 * u * u * s * p1 + 3 * u * s * s * p2 + s * s * s * p3;
    }

    private static double solve(double x0, double x1, double x2, double x3, double x) {
        double low = 0;
        double high = 1;
        double s = (x - x0) / (x3 - x0);
        for (int step = 0; step < SOLVE_STEPS; step++) {
            double at = cubic(x0, x1, x2, x3, s);
            if (Math.abs(at - x) < 1e-9) return s;
            if (at < x) low = s;
            else high = s;
            double slope = 3 * (1 - s) * (1 - s) * (x1 - x0) + 6 * (1 - s) * s * (x2 - x1) + 3 * s * s * (x3 - x2);
            double next = Math.abs(slope) > 1e-9 ? s - (at - x) / slope : (low + high) * 0.5;
            s = next > low && next < high ? next : (low + high) * 0.5;
        }
        return s;
    }
}
