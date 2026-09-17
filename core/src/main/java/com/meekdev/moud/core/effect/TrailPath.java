package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.List;

public final class TrailPath {

    public record Point(Vector3 a, Vector3 b, double time, double travelled) {

        public Vector3 middle() {
            return a.add(b).mul(0.5);
        }
    }

    private final List<Point> points = new ArrayList<>();
    private double travelled;

    public List<Point> points() {
        return points;
    }

    public void clear() {
        points.clear();
        travelled = 0;
    }

    public void follow(Vector3 a, Vector3 b, double now, double minLength) {
        Vector3 middle = a.add(b).mul(0.5);
        if (points.isEmpty()) {
            points.add(new Point(a, b, now, travelled));
            return;
        }
        double step = middle.distance(points.getLast().middle());
        if (step < Math.max(minLength, 1e-4)) return;
        travelled += step;
        points.add(new Point(a, b, now, travelled));
    }

    public void expire(double now, double lifetime) {
        int dead = 0;
        while (dead < points.size() && now - points.get(dead).time() >= lifetime) dead++;
        if (dead > 0) points.subList(0, dead).clear();
    }

    public void limit(double maxLength, Vector3 head) {
        if (maxLength <= 0 || points.isEmpty()) return;
        double length = 0;
        Vector3 previous = head;
        for (int n = points.size() - 1; n >= 0; n--) {
            Vector3 at = points.get(n).middle();
            length += at.distance(previous);
            previous = at;
            if (length > maxLength) {
                points.subList(0, n).clear();
                return;
            }
        }
    }

    public double travelled() {
        return travelled;
    }
}
