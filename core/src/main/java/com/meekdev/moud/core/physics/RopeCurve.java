package com.meekdev.moud.core.physics;

import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.List;

public final class RopeCurve {

    public record Link(Vector3 position, Vector3 forward, Vector3 up) {}

    private static final Vector3 DOWN = new Vector3(0, -1, 0);

    private RopeCurve() {}

    public static Vector3 at(Vector3 from, Vector3 to, double length, double t) {
        double apart = to.sub(from).length();
        double slack = Math.max(0, length - apart);
        Vector3 straight = from.lerp(to, t);
        if (slack <= 1.0e-4) return straight;
        double sag = apart < 1.0e-3 ? length * 0.5 : Math.min(length * 0.5, Math.sqrt(3 * apart * slack / 8));
        return straight.add(DOWN.mul(sag * 4 * t * (1 - t)));
    }

    public static List<Vector3> points(Vector3 from, Vector3 to, double length, int segments) {
        List<Vector3> points = new ArrayList<>(segments + 1);
        for (int n = 0; n <= segments; n++) points.add(at(from, to, length, (double) n / segments));
        return points;
    }

    public static List<Link> links(List<Vector3> points, double spacing, double twist, int most) {
        List<Link> links = new ArrayList<>();
        if (points.size() < 2 || !(spacing > 0)) return links;
        double[] reach = new double[points.size()];
        for (int n = 1; n < points.size(); n++) reach[n] = reach[n - 1] + points.get(n).sub(points.get(n - 1)).length();
        double total = reach[reach.length - 1];
        Vector3 up = null;
        int segment = 1;
        for (int k = 0; k < most; k++) {
            double along = spacing * (k + 0.5);
            if (along > total) break;
            while (segment < points.size() - 1 && reach[segment] < along) segment++;
            Vector3 a = points.get(segment - 1);
            Vector3 b = points.get(segment);
            double span = reach[segment] - reach[segment - 1];
            if (span < 1.0e-9) continue;
            Vector3 forward = b.sub(a).mul(1 / span);
            Vector3 position = a.add(forward.mul(along - reach[segment - 1]));
            Vector3 carried = up == null ? Vector3.ZERO : up.sub(forward.mul(up.dot(forward)));
            if (carried.lengthSq() < 1.0e-10) {
                carried = forward.cross(Math.abs(forward.y()) < 0.9 ? new Vector3(0, 1, 0) : new Vector3(1, 0, 0)).cross(forward);
            }
            up = carried.normalize();
            double roll = twist * k;
            Vector3 rolled = up.mul(Math.cos(roll)).add(forward.cross(up).mul(Math.sin(roll)));
            links.add(new Link(position, forward, rolled));
        }
        return links;
    }
}
