package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.math.Vec3;
import java.util.function.Predicate;

// what a ray runs into
//
// every part is a box with a turn on it, so this is the same test six times over a body: take the
// ray into the box's own frame, where the box is axis aligned and centred on nothing, and ask the
// three pairs of planes when the ray was between them. the nearest answer wins
//
// a limb is tested even though no limb collides. what a body walks into and what a bullet finds
// are different questions, and answering the second with the first is a character who can be shot
// through the head because the head is not solid
public final class Hits {

    // where it landed and on what. the part is the limb, so the caller learns which one without
    // a second lookup
    public record Hit(Part part, Vec3 at, double distance) {}

    private Hits() {}

    public static Hit cast(Instance root, Vec3 from, Vec3 direction, double range) {
        return cast(root, from, direction, range, part -> true);
    }

    // only parts the filter takes can be hit, and the rest are seen through
    public static Hit cast(Instance root, Vec3 from, Vec3 direction, double range, Predicate<Part> filter) {
        if (root == null || range <= 0) return null;
        double length = direction.length();
        if (length < 1e-12) return null;
        return nearest(root, from, direction.mul(1.0 / length), range, null, filter);
    }

    private static Hit nearest(Instance instance, Vec3 from, Vec3 way, double range, Hit best,
                               Predicate<Part> filter) {
        // a shell is a second coat of paint over a limb, standing a quarter of a texel proud of
        // it. it is in front of everything it covers, so leaving it in means every hit on a body
        // answers "the hat" and never "the head"
        if (instance instanceof Part part && part.visible && !isShell(part) && filter.test(part)) {
            double at = enters(part, from, way, range);
            if (at >= 0 && (best == null || at < best.distance())) {
                best = new Hit(part, from.add(way.mul(at)), at);
            }
        }
        for (Instance child : instance.children()) best = nearest(child, from, way, range, best, filter);
        return best;
    }

    // how far along the ray the box begins, or a negative number for never
    //
    // the ray is taken into the box rather than the box turned into the world, because a turned
    // box is eight corners and a turned ray is two vectors
    private static double enters(Part part, Vec3 from, Vec3 way, double range) {
        var frame = Transforms.world(part);
        Vec3 origin = frame.pointToObject(from);
        Vec3 heading = frame.vectorToObject(way);
        Vec3 half = part.size.mul(0.5);

        double near = 0;
        double far = range;
        for (int axis = 0; axis < 3; axis++) {
            double o = component(origin, axis);
            double d = component(heading, axis);
            double h = component(half, axis);
            if (Math.abs(d) < 1e-12) {
                // parallel to this pair of planes: either always between them or never
                if (o < -h || o > h) return -1;
                continue;
            }
            double one = (-h - o) / d;
            double two = (h - o) / d;
            if (one > two) {
                double swap = one;
                one = two;
                two = swap;
            }
            near = Math.max(near, one);
            far = Math.min(far, two);
            if (near > far) return -1;
        }
        return near;
    }

    private static boolean isShell(Part part) {
        return Rig.OVERLAY.equals(part.name()) && part.parent() instanceof Part;
    }

    private static double component(Vec3 v, int axis) {
        return axis == 0 ? v.x() : axis == 1 ? v.y() : v.z();
    }
}
