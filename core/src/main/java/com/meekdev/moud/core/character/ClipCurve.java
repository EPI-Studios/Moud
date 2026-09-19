package com.meekdev.moud.core.character;

import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.tween.Easing;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ClipCurve {

    public enum Interp { LINEAR, CATMULLROM, BEZIER, STEP, EASED }

    public record Handle(double time, double[] value) {}

    public record Key(double time, double[] value, Interp interp, Easing easing, Easing.Direction direction, Handle in, Handle out) {

        public static Key of(double time, double[] value, Interp interp) {
            return new Key(time, value, interp, Easing.LINEAR, Easing.Direction.IN_OUT, null, null);
        }
    }

    private static final int BISECTIONS = 32;

    private final List<Key> keys;

    public ClipCurve(List<Key> keys) {
        if (keys.isEmpty()) throw new IllegalArgumentException("a curve needs at least one key");
        List<Key> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator.comparingDouble(Key::time));
        this.keys = List.copyOf(sorted);
    }

    public List<Key> keys() {
        return keys;
    }

    public double end() {
        return keys.getLast().time();
    }

    public int width() {
        return keys.getFirst().value().length;
    }

    public double[] sample(double time) {
        int at = -1;
        for (int n = 0; n < keys.size() && keys.get(n).time() <= time; n++) at = n;
        if (at < 0) return keys.getFirst().value();
        if (at == keys.size() - 1) return keys.getLast().value();
        Key from = keys.get(at);
        Key to = keys.get(at + 1);
        double alpha = (time - from.time()) / (to.time() - from.time());
        if (from.value().length == 4) return quat(from, to, alpha);
        return switch (from.interp()) {
            case STEP -> from.value();
            case LINEAR -> lerp(from.value(), to.value(), alpha);
            case EASED -> lerp(from.value(), to.value(), from.easing().apply(alpha, from.direction()));
            case CATMULLROM -> catmull(at, alpha);
            case BEZIER -> bezier(from, to, time);
        };
    }

    private static double[] quat(Key from, Key to, double alpha) {
        double t = switch (from.interp()) {
            case STEP -> 0;
            case EASED -> from.easing().apply(alpha, from.direction());
            default -> alpha;
        };
        Quat a = new Quat(from.value()[0], from.value()[1], from.value()[2], from.value()[3]);
        Quat b = new Quat(to.value()[0], to.value()[1], to.value()[2], to.value()[3]);
        Quat q = a.slerp(b, t).normalize();
        return new double[] {q.x(), q.y(), q.z(), q.w()};
    }

    private static double[] lerp(double[] a, double[] b, double t) {
        double[] out = new double[a.length];
        for (int c = 0; c < a.length; c++) out[c] = a[c] + (b[c] - a[c]) * t;
        return out;
    }

    private double[] catmull(int at, double t) {
        double[] p1 = keys.get(at).value();
        double[] p2 = keys.get(at + 1).value();
        double[] p0 = at > 0 ? keys.get(at - 1).value() : p1;
        double[] p3 = at + 2 < keys.size() ? keys.get(at + 2).value() : p2;
        double t2 = t * t;
        double t3 = t2 * t;
        double[] out = new double[p1.length];
        for (int c = 0; c < out.length; c++) {
            out[c] = 0.5 * (2 * p1[c] + (p2[c] - p0[c]) * t
                    + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * t2
                    + (3 * p1[c] - p0[c] - 3 * p2[c] + p3[c]) * t3);
        }
        return out;
    }

    private static double[] bezier(Key from, Key to, double time) {
        double span = to.time() - from.time();
        double outTime = Math.clamp(from.out() == null ? span / 3 : from.out().time(), 0, span);
        double inTime = Math.clamp(to.in() == null ? -span / 3 : to.in().time(), -span, 0);
        double x0 = from.time();
        double x1 = from.time() + outTime;
        double x2 = to.time() + inTime;
        double x3 = to.time();
        double low = 0;
        double high = 1;
        for (int n = 0; n < BISECTIONS; n++) {
            double mid = (low + high) * 0.5;
            if (cubic(x0, x1, x2, x3, mid) < time) low = mid;
            else high = mid;
        }
        double s = (low + high) * 0.5;
        double[] out = new double[from.value().length];
        for (int c = 0; c < out.length; c++) {
            double v0 = from.value()[c];
            double v3 = to.value()[c];
            double v1 = v0 + component(from.out(), c);
            double v2 = v3 + component(to.in(), c);
            out[c] = cubic(v0, v1, v2, v3, s);
        }
        return out;
    }

    private static double component(Handle handle, int c) {
        if (handle == null || handle.value().length == 0) return 0;
        return handle.value()[Math.min(c, handle.value().length - 1)];
    }

    private static double cubic(double a, double b, double c, double d, double s) {
        double r = 1 - s;
        return r * r * r * a + 3 * r * r * s * b + 3 * r * s * s * c + s * s * s * d;
    }
}
