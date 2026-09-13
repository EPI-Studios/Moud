package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.math.Aabb;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.WeakHashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

// what is where: casts that stop at the first part in the way, and overlaps that list every part in
// a volume. every part is a box with a turn on it
//
// a part that is not visible is not there, the way a removed limb is not there. a zone that should be
// found but not seen is transparent instead
public final class Queries {

    // which parts a query may find. listed instances count with everything under them, and a group
    // leaves out the parts that group passes through
    public record Filter(List<Instance> instances, boolean include, boolean respectCollides,
                         CollisionGroups groups, String group, String tag, ClassDef<?> className) implements Predicate<Part> {

        public static final Filter ALL = new Filter(List.of(), false, false);

        public Filter(List<Instance> instances, boolean include, boolean respectCollides) {
            this(instances, include, respectCollides, null, null, null, null);
        }

        public Filter(List<Instance> instances, boolean include, boolean respectCollides,
                      CollisionGroups groups, String group) {
            this(instances, include, respectCollides, groups, group, null, null);
        }

        @Override
        public boolean test(Part part) {
            if (respectCollides && !part.collides) return false;
            if (tag != null && !part.hasTag(tag)) return false;
            if (className != null && !part.def().isA(className)) return false;
            if (groups != null && !groups.collide(group, CollisionGroups.groupOf(part))) return false;
            if (instances.isEmpty()) return !include;
            boolean listed = false;
            for (Instance at = part; at != null && !listed; at = at.parent()) listed = instances.contains(at);
            return listed == include;
        }
    }

    // the first part in the way: where it was met, which way that face looks, and how far along
    public record Cast(Part part, Vec3 at, Vec3 normal, double distance) {}

    record Box(Vec3 centre, Vec3[] axes, Vec3 half) {

        static Box of(Part part) {
            return of(FRAMES.get().apply(part), part.size);
        }

        static Box of(CFrame frame, Vec3 size) {
            Vec3[] axes = {
                frame.rotation().rotate(Vec3.RIGHT),
                frame.rotation().rotate(Vec3.UP),
                frame.rotation().rotate(new Vec3(0, 0, 1)),
            };
            return new Box(frame.position(), axes, size.mul(0.5));
        }

        double extent(int axis) {
            return axis == 0 ? half.x() : axis == 1 ? half.y() : half.z();
        }

        // how far this box reaches along a direction, either way from its centre
        double reach(Vec3 direction) {
            return half.x() * Math.abs(direction.dot(axes[0])) + half.y() * Math.abs(direction.dot(axes[1]))
                    + half.z() * Math.abs(direction.dot(axes[2]));
        }

        Vec3 closest(Vec3 point) {
            Vec3 offset = point.sub(centre);
            Vec3 result = centre;
            for (int i = 0; i < 3; i++) {
                double d = Math.clamp(offset.dot(axes[i]), -extent(i), extent(i));
                result = result.add(axes[i].mul(d));
            }
            return result;
        }
    }

    // where a part is as far as a query is concerned: where it is now, unless a rewind says otherwise.
    // per thread, because the server and a client query from their own threads in one process
    private static final Function<Part, CFrame> NOW = Transforms::world;
    private static final ThreadLocal<Function<Part, CFrame>> FRAMES = ThreadLocal.withInitial(() -> NOW);

    private Queries() {}

    // runs query with every part where frames puts it, which is how a shot is tested against where the
    // shooter saw things rather than where they are now
    public static <T> T rewound(Function<Part, CFrame> frames, Supplier<T> query) {
        Function<Part, CFrame> before = FRAMES.get();
        FRAMES.set(frames);
        try {
            return query.get();
        } finally {
            FRAMES.set(before);
        }
    }

    // a ray that starts inside a part does not hit that part, so a ray from inside a head passes out of it
    public static Cast raycast(Instance root, Vec3 from, Vec3 direction, double range, Predicate<Part> filter) {
        return (Cast) remembered(root, List.of("ray", from, direction, range, filter),
                () -> swept(root, from, direction, range, 0, filter));
    }

    // the same question asked twice while nothing in the tree changed gets the answer it got the first
    // time, which is what a place asking every frame from several scripts mostly does
    private record Memo(long stamp, Map<List<Object>, Object> answers) {}

    private static final Map<InstanceTree, Memo> MEMOS = Collections.synchronizedMap(new WeakHashMap<>());

    private static Object remembered(Instance root, List<Object> question, Supplier<Object> ask) {
        if (root == null || root.tree() == null || FRAMES.get() != NOW || !(question.getLast() instanceof Record)) {
            return ask.get();
        }
        InstanceTree tree = root.tree();
        long stamp = tree.mutations();
        Memo memo = MEMOS.get(tree);
        if (memo == null || memo.stamp() != stamp) {
            memo = new Memo(stamp, new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<List<Object>, Object> eldest) {
                    return size() > 256;
                }
            });
            MEMOS.put(tree, memo);
        }
        List<Object> key = new ArrayList<>(question);
        key.add(root);
        synchronized (memo.answers()) {
            if (memo.answers().containsKey(key)) return memo.answers().get(key);
        }
        Object answer = ask.get();
        synchronized (memo.answers()) {
            memo.answers().put(key, answer);
        }
        return answer;
    }

    // a ball moved along a direction. its edges and corners are met as if the part were a box grown by
    // the radius, which can be early by at most that radius times 0.73 past a corner
    public static Cast spherecast(Instance root, Vec3 from, double radius, Vec3 direction, double range,
                                  Predicate<Part> filter) {
        return swept(root, from, direction, range, Math.max(0, radius), filter);
    }

    private static Cast swept(Instance root, Vec3 from, Vec3 direction, double range, double grow, Predicate<Part> filter) {
        if (root == null || range <= 0 || direction.lengthSq() < 1e-24) return null;
        Vec3 way = direction.normalize();
        Cast best = null;
        for (Part part : parts(root, segment(from, way, range, grow), filter)) {
            Cast hit = sweptHit(part, from, way, range, grow);
            if (hit != null && (best == null || hit.distance() < best.distance())) best = hit;
        }
        return best;
    }

    // every part a ray passes into, nearest first
    public static List<Cast> raycastAll(Instance root, Vec3 from, Vec3 direction, double range, Predicate<Part> filter) {
        List<Cast> hits = new ArrayList<>();
        if (root == null || range <= 0 || direction.lengthSq() < 1e-24) return hits;
        Vec3 way = direction.normalize();
        for (Part part : parts(root, segment(from, way, range, 0), filter)) {
            Cast hit = sweptHit(part, from, way, range, 0);
            if (hit != null) hits.add(hit);
        }
        hits.sort((a, b) -> Double.compare(a.distance(), b.distance()));
        return hits;
    }

    private static Aabb segment(Vec3 from, Vec3 way, double range, double grow) {
        Vec3 to = from.add(way.mul(range));
        return new Aabb(Math.min(from.x(), to.x()), Math.min(from.y(), to.y()), Math.min(from.z(), to.z()),
                Math.max(from.x(), to.x()), Math.max(from.y(), to.y()), Math.max(from.z(), to.z())).grow(grow);
    }

    private static Cast sweptHit(Part part, Vec3 from, Vec3 way, double range, double grow) {
        Box box = Box.of(part);
        double near = 0;
        double far = range;
        int nearAxis = -1;
        double nearSign = 0;
        for (int i = 0; i < 3; i++) {
            double o = from.sub(box.centre()).dot(box.axes()[i]);
            double d = way.dot(box.axes()[i]);
            double h = box.extent(i) + grow;
            if (Math.abs(d) < 1e-12) {
                if (o < -h || o > h) return null;
                continue;
            }
            double one = (-h - o) / d;
            double two = (h - o) / d;
            double sign = -1;
            if (one > two) {
                double swap = one;
                one = two;
                two = swap;
                sign = 1;
            }
            if (one > near) {
                near = one;
                nearAxis = i;
                nearSign = sign;
            }
            far = Math.min(far, two);
            if (near > far) return null;
        }
        // nearAxis stays unset when the start is already inside
        if (nearAxis < 0) return null;
        Vec3 normal = box.axes()[nearAxis].mul(nearSign);
        Vec3 centre = from.add(way.mul(near));
        return new Cast(part, centre.sub(normal.mul(grow)), normal, near);
    }

    // a box moved along a direction, met exactly on every face, edge and corner
    public static Cast blockcast(Instance root, CFrame frame, Vec3 size, Vec3 direction, double range,
                                 Predicate<Part> filter) {
        if (root == null || range <= 0 || direction.lengthSq() < 1e-24) return null;
        Vec3 way = direction.normalize();
        Box moving = Box.of(frame, size);
        Cast best = null;
        Aabb start = SpatialIndex.bounds(frame, size);
        Aabb end = SpatialIndex.bounds(new CFrame(frame.position().add(way.mul(range)), frame.rotation()), size);
        Aabb swept = new Aabb(Math.min(start.minX(), end.minX()), Math.min(start.minY(), end.minY()),
                Math.min(start.minZ(), end.minZ()), Math.max(start.maxX(), end.maxX()),
                Math.max(start.maxY(), end.maxY()), Math.max(start.maxZ(), end.maxZ()));
        for (Part part : parts(root, swept, filter)) {
            Box still = Box.of(part);
            double enter = 0;
            double exit = range;
            Vec3 enterAxis = null;
            boolean missed = false;
            for (Vec3 axis : axes(moving, still)) {
                double gap = still.centre().sub(moving.centre()).dot(axis);
                double reach = moving.reach(axis) + still.reach(axis);
                double speed = way.dot(axis);
                if (Math.abs(speed) < 1e-12) {
                    if (Math.abs(gap) > reach) {
                        missed = true;
                        break;
                    }
                    continue;
                }
                double one = (gap - reach) / speed;
                double two = (gap + reach) / speed;
                if (one > two) {
                    double swap = one;
                    one = two;
                    two = swap;
                }
                if (one > enter) {
                    enter = one;
                    enterAxis = gap > 0 ? axis.neg() : axis;
                }
                exit = Math.min(exit, two);
                if (enter > exit) {
                    missed = true;
                    break;
                }
            }
            if (missed || enterAxis == null) continue;
            if (best == null || enter < best.distance()) {
                Vec3 centre = moving.centre().add(way.mul(enter));
                best = new Cast(part, still.closest(centre), enterAxis, enter);
            }
        }
        return best;
    }

    @SuppressWarnings("unchecked")
    public static List<Part> inBox(Instance root, CFrame frame, Vec3 size, Predicate<Part> filter) {
        return new ArrayList<>((List<Part>) remembered(root, List.of("box", frame, size, filter),
                () -> List.copyOf(boxed(root, frame, size, filter))));
    }

    private static List<Part> boxed(Instance root, CFrame frame, Vec3 size, Predicate<Part> filter) {
        Box box = Box.of(frame, size);
        List<Part> found = new ArrayList<>();
        for (Part part : parts(root, SpatialIndex.bounds(frame, size), filter)) {
            if (overlap(box, Box.of(part), 0)) found.add(part);
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    public static List<Part> inRadius(Instance root, Vec3 centre, double radius, Predicate<Part> filter) {
        return new ArrayList<>((List<Part>) remembered(root, List.of("radius", centre, radius, filter),
                () -> List.copyOf(around(root, centre, radius, filter))));
    }

    private static List<Part> around(Instance root, Vec3 centre, double radius, Predicate<Part> filter) {
        List<Part> found = new ArrayList<>();
        Aabb around = new Aabb(centre.x() - radius, centre.y() - radius, centre.z() - radius,
                centre.x() + radius, centre.y() + radius, centre.z() + radius);
        for (Part part : parts(root, around, filter)) {
            if (Box.of(part).closest(centre).sub(centre).lengthSq() <= radius * radius) found.add(part);
        }
        return found;
    }

    // everything the part's own box overlaps, the part and anything under it left out
    public static List<Part> inPart(Instance root, Part part, Predicate<Part> filter) {
        Box box = Box.of(part);
        List<Part> found = new ArrayList<>();
        for (Part other : parts(root, SpatialIndex.bounds(FRAMES.get().apply(part), part.size), filter)) {
            if (other == part || isUnder(other, part)) continue;
            if (overlap(box, Box.of(other), 0)) found.add(other);
        }
        return found;
    }

    // overlapping or within margin of it, which is what counts as touching
    public static boolean touching(Part a, Part b, double margin) {
        return overlap(Box.of(a), Box.of(b), margin);
    }

    static boolean overlap(Box a, Box b, double margin) {
        Vec3 gap = b.centre().sub(a.centre());
        for (Vec3 axis : axes(a, b)) {
            if (Math.abs(gap.dot(axis)) > a.reach(axis) + b.reach(axis) + margin) return false;
        }
        return true;
    }

    // the fifteen directions two boxes can be told apart along: the faces of each, and every pair of edges
    private static List<Vec3> axes(Box a, Box b) {
        List<Vec3> axes = new ArrayList<>(15);
        for (Vec3 axis : a.axes()) axes.add(axis);
        for (Vec3 axis : b.axes()) axes.add(axis);
        for (Vec3 one : a.axes()) {
            for (Vec3 two : b.axes()) {
                Vec3 edge = one.cross(two);
                // parallel edges give nothing the faces have not already said
                if (edge.lengthSq() > 1e-10) axes.add(edge.normalize());
            }
        }
        return axes;
    }

    static List<Part> parts(Instance root, Predicate<Part> filter) {
        List<Part> found = new ArrayList<>();
        collect(root, filter, found);
        return found;
    }

    // the parts under root that may touch region, from the tree's index. a rewind asks where things were,
    // which the index does not know, so that one walks the branch
    // how many queries ran and how many parts they tested, for a place checking what its queries cost
    private static final AtomicLong QUERIES = new AtomicLong();
    private static final AtomicLong TESTED = new AtomicLong();

    // queries and parts tested since the last call, and starts counting again
    public static long[] takeStats() {
        return new long[] {QUERIES.getAndSet(0), TESTED.getAndSet(0)};
    }

    static List<Part> parts(Instance root, Aabb region, Predicate<Part> filter) {
        QUERIES.incrementAndGet();
        if (root == null || root.tree() == null || FRAMES.get() != NOW) return parts(root, filter);
        List<Part> candidates = new ArrayList<>();
        root.tree().spatial().candidates(region, candidates);
        TESTED.addAndGet(candidates.size());
        List<Part> found = new ArrayList<>(candidates.size());
        boolean everything = root.parent() == null;
        for (Part part : candidates) {
            if (!part.visible || !part.canQuery || isShell(part) || !filter.test(part)) continue;
            if (everything || part == root || isUnder(part, root)) found.add(part);
        }
        return found;
    }

    private static void collect(Instance instance, Predicate<Part> filter, List<Part> found) {
        if (instance instanceof Part part && part.visible && part.canQuery && !isShell(part) && filter.test(part)) {
            found.add(part);
        }
        for (Instance child : instance.children()) collect(child, filter, found);
    }

    static boolean isUnder(Instance instance, Instance ancestor) {
        for (Instance at = instance.parent(); at != null; at = at.parent()) {
            if (at == ancestor) return true;
        }
        return false;
    }

    // a shell is a second coat of paint over a limb, standing a quarter of a texel proud of it, so a
    // hit on a body would always answer "the hat" and never "the head"
    private static boolean isShell(Part part) {
        return Rig.OVERLAY.equals(part.name()) && part.parent() instanceof Part;
    }
}
