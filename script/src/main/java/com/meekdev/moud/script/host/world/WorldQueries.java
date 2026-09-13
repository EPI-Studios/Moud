package com.meekdev.moud.script.host.world;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Aabb;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.CollisionGroups;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.query.Queries;
import com.meekdev.moud.core.query.SpatialIndex;
import com.meekdev.moud.script.api.BlockRef;
import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.Members;
import com.meekdev.moud.script.host.Results;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorldQueries {

    private static final double SKY = 320;
    private static final double DEPTH = 400;

    private record Params(Queries.Filter filter, boolean ignoreBlocks, int limit, boolean sorted) {
        static final Params NONE = new Params(Queries.Filter.ALL, false, Integer.MAX_VALUE, false);
    }

    private record Hit(Vector3 at, Vector3 normal) {}

    private WorldQueries() {}

    public static void install(Host host) {
        host.api().alias("RayHit", "{ part: Instance, position: Vector3, distance: number, normal: Vector3 }");
        host.api().alias("QueryOptions", "{ exclude: { Instance }?, include: { Instance }?, respectCollides: boolean?, "
                + "collisionGroup: string?, tag: string?, className: string?, limit: number?, sorted: boolean?, ignoreBlocks: boolean? }");

        Members shared = host.instances().shared();
        shared.method("raycast", "(from: Vector3, direction: Vector3, range: number?, options: QueryOptions?) -> (Instance?, Vector3?, number?, Vector3?, string?)",
                a -> raycast(host, a));
        shared.method("spherecast", "(from: Vector3, radius: number, direction: Vector3, range: number?, options: QueryOptions?) -> (Instance?, Vector3?, number?, Vector3?)",
                a -> cast(Queries.spherecast(a.self(), a.vector(1), a.number(2), a.vector(3), a.number(4, 100), params(host, a, 5).filter())));
        shared.method("blockcast", "(frame: CFrame, size: Vector3, direction: Vector3, range: number?, options: QueryOptions?) -> (Instance?, Vector3?, number?, Vector3?)",
                a -> cast(Queries.blockcast(a.self(), a.cframe(1), a.vector(2), a.vector(3), a.number(4, 100), params(host, a, 5).filter())));
        shared.method("partsInBox", "(frame: CFrame, size: Vector3, options: QueryOptions?) -> { Instance }", a -> {
            Params params = params(host, a, 3);
            return shape(Queries.inBox(a.self(), a.cframe(1), a.vector(2), params.filter()), a.cframe(1).position(), params);
        });
        shared.method("partsInRadius", "(position: Vector3, radius: number, options: QueryOptions?) -> { Instance }", a -> {
            Params params = params(host, a, 3);
            return shape(Queries.inRadius(a.self(), a.vector(1), a.number(2), params.filter()), a.vector(1), params);
        });
        shared.method("partsInPart", "(part: Instance, options: QueryOptions?) -> { Instance }", a -> {
            if (!(a.get(1) instanceof Part part)) throw a.error("expects a part");
            Params params = params(host, a, 2);
            return shape(Queries.inPart(a.self(), part, params.filter()), Transforms.world(part).position(), params);
        });
        shared.method("raycastAll", "(from: Vector3, direction: Vector3, range: number?, options: QueryOptions?) -> { RayHit }",
                a -> raycastAll(host, a));
        shared.method("partsAlongRay", "(from: Vector3, direction: Vector3, range: number?, options: QueryOptions?) -> { RayHit }",
                a -> raycastAll(host, a));
        shared.method("raycastMany", "(rays: { { any } }, options: QueryOptions?) -> { RayHit | false }", a -> {
            Instance root = a.self();
            Params params = params(host, a, 2);
            List<Object> out = new ArrayList<>();
            for (Object entry : a.list(1)) {
                if (!(entry instanceof List<?> ray) || ray.size() < 2
                        || !(ray.get(0) instanceof Vector3 from) || !(ray.get(1) instanceof Vector3 direction)) {
                    throw a.error("expects a list of { from, direction, range }");
                }
                double range = ray.size() > 2 && ray.get(2) instanceof Number n ? n.doubleValue() : 100;
                Queries.Cast cast = Queries.raycast(root, from, direction, range, params.filter());
                out.add(cast == null ? Boolean.FALSE : hit(cast));
            }
            return out;
        });
        shared.method("nearestPart", "(position: Vector3, radius: number, options: QueryOptions?) -> (Instance?, number?)", a -> {
            Vector3 at = a.vector(1);
            return nearest(Queries.inRadius(a.self(), at, a.number(2), params(host, a, 3).filter()), at);
        });
        shared.method("nearestTagged", "(position: Vector3, tag: string, radius: number?) -> (Instance?, number?)", a -> {
            Vector3 at = a.vector(1);
            Queries.Filter filter = new Queries.Filter(List.of(), false, false, null, null, a.string(2), null);
            return nearest(Queries.inRadius(a.self(), at, a.number(3, 256), filter), at);
        });
        shared.method("capsulecast", "(from: Vector3, to: Vector3, radius: number, options: QueryOptions?) -> (Instance?, Vector3?, number?, Vector3?)", a -> {
            Vector3 from = a.vector(1);
            Vector3 to = a.vector(2);
            return cast(Queries.spherecast(a.self(), from, a.number(3), to.sub(from), to.sub(from).length(), params(host, a, 4).filter()));
        });
        shared.method("sweep", "(part: Instance, direction: Vector3, distance: number, options: QueryOptions?) -> (Instance?, Vector3?, number?, Vector3?)", a -> {
            if (!(a.get(1) instanceof Part part)) throw a.error("expects a part");
            Queries.Filter base = params(host, a, 4).filter();
            List<Instance> ignore = new ArrayList<>(base.include() ? List.of() : base.instances());
            ignore.add(part);
            Queries.Filter filter = new Queries.Filter(ignore, false, base.respectCollides(), base.groups(), base.group(),
                    base.tag(), base.className());
            return cast(Queries.blockcast(a.self(), Transforms.world(part), part.size, a.vector(2), a.number(3), filter));
        });
        shared.method("partsAtPoint", "(position: Vector3, options: QueryOptions?) -> { Instance }", a -> {
            Vector3 at = a.vector(1);
            List<Part> found = Queries.inRadius(a.self(), at, 0, params(host, a, 2).filter());
            found.removeIf(p -> !inside(Transforms.world(p), p.size, at));
            return new ArrayList<Object>(found);
        });
        shared.method("boundsOf", "(instances: { Instance }) -> (CFrame?, Vector3?)", a -> {
            double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (Object entry : a.list(1)) {
                if (entry instanceof Instance instance) grow(box, instance);
            }
            if (box[0] > box[3]) return null;
            return Results.of(CFrame.at((box[0] + box[3]) / 2, (box[1] + box[4]) / 2, (box[2] + box[5]) / 2),
                    new Vector3(box[3] - box[0], box[4] - box[1], box[5] - box[2]));
        });
        shared.method("groundAt", "(x: number, z: number, from: number?, options: QueryOptions?) -> (number?, Vector3?)", a -> {
            Hit hit = down(host, a.self(), new Vector3(a.number(1), a.number(3, SKY), a.number(2)), DEPTH, params(host, a, 4).filter());
            return hit == null ? null : Results.of(hit.at().y(), hit.normal());
        });
        shared.method("surfaceNormal", "(position: Vector3, options: QueryOptions?) -> Vector3?", a -> {
            Hit hit = down(host, a.self(), a.vector(1).add(new Vector3(0, 1, 0)), 3, params(host, a, 2).filter());
            return hit == null ? null : hit.normal();
        });
        shared.method("heightmap", "(from: Vector3, to: Vector3, step: number?, options: QueryOptions?) -> { { number } }", a -> heightmap(host, a));
        shared.method("findFreeSpot", "(near: Vector3, size: Vector3, radius: number?) -> Vector3?", a -> freeSpot(host, a));

        Members parts = host.instances().of(Classes.PART);
        parts.method("overlapping", "(options: QueryOptions?) -> { Instance }", a -> {
            Part part = a.self(Part.class);
            Instance root = part.tree().root();
            return new ArrayList<Object>(Queries.inPart(root, part, params(host, a, 1).filter()));
        });
        parts.method("closestPoint", "(position: Vector3) -> Vector3", a -> {
            Part part = a.self(Part.class);
            CFrame frame = Transforms.world(part);
            return frame.pointToWorld(clamp(frame.pointToObject(a.vector(1)), part.size.mul(0.5)));
        });
        parts.method("contains", "(position: Vector3) -> boolean", a -> {
            Part part = a.self(Part.class);
            return inside(Transforms.world(part), part.size, a.vector(1));
        });
        parts.method("bounds", "() -> (CFrame, Vector3)", a -> {
            Part part = a.self(Part.class);
            return Results.of(Transforms.world(part), part.size);
        });
        parts.method("worldBounds", "() -> (Vector3, Vector3)", a -> {
            Part part = a.self(Part.class);
            Aabb box = SpatialIndex.bounds(Transforms.world(part), part.size);
            return Results.of(new Vector3(box.minX(), box.minY(), box.minZ()), new Vector3(box.maxX(), box.maxY(), box.maxZ()));
        });
    }

    public static boolean inside(CFrame frame, Vector3 size, Vector3 point) {
        Vector3 local = frame.inverse().mul(CFrame.at(point)).position();
        return Math.abs(local.x()) <= size.x() / 2 && Math.abs(local.y()) <= size.y() / 2 && Math.abs(local.z()) <= size.z() / 2;
    }

    public static boolean clear(Host host, Instance root, Vector3 from, Vector3 to, List<Instance> ignore) {
        Vector3 way = to.sub(from);
        double length = way.length();
        if (length < 1e-6) return true;
        if (Queries.raycast(root, from, way, length, new Queries.Filter(ignore, false, true)) != null) return false;
        BlockRef blocks = host.blocks();
        return blocks == null || root.parent() != null || blocks.raycast(from, way, length) == null;
    }

    private static Object raycast(Host host, Args a) {
        Instance root = a.self();
        Vector3 from = a.vector(1);
        Vector3 direction = a.vector(2);
        double range = a.number(3, 100);
        Params params = params(host, a, 4);
        Queries.Cast hit = Queries.raycast(root, from, direction, range, params.filter());
        BlockRef blocks = host.blocks();
        if (!params.ignoreBlocks() && blocks != null && root.parent() == null) {
            BlockRef.Hit block = blocks.raycast(from, direction, hit == null ? range : hit.distance());
            if (block != null && (hit == null || block.distance() < hit.distance())) {
                return Results.of(null, block.at(), block.distance(), block.normal(), block.block());
            }
        }
        return cast(hit);
    }

    private static Object raycastAll(Host host, Args a) {
        Params params = params(host, a, 4);
        List<Queries.Cast> hits = Queries.raycastAll(a.self(), a.vector(1), a.vector(2), a.number(3, 100), params.filter());
        List<Object> out = new ArrayList<>();
        for (int n = 0; n < Math.min(hits.size(), params.limit()); n++) out.add(hit(hits.get(n)));
        return out;
    }

    public static Object cast(Queries.Cast hit) {
        return hit == null ? null : Results.of(hit.part(), hit.at(), hit.distance(), hit.normal());
    }

    private static Map<String, Object> hit(Queries.Cast cast) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("part", cast.part());
        out.put("position", cast.at());
        out.put("distance", cast.distance());
        out.put("normal", cast.normal());
        return out;
    }

    private static List<Object> shape(List<Part> parts, Vector3 from, Params params) {
        if (params.sorted()) {
            parts.sort((x, y) -> Double.compare(Transforms.world(x).position().sub(from).lengthSq(),
                    Transforms.world(y).position().sub(from).lengthSq()));
        }
        return new ArrayList<>(parts.size() > params.limit() ? parts.subList(0, params.limit()) : parts);
    }

    public static Queries.Filter filter(Host host, Args a, int at) {
        return params(host, a, at).filter();
    }

    private static Params params(Host host, Args a, int at) {
        if (!a.has(at)) return Params.NONE;
        Map<String, Object> options = a.map(at);
        List<Instance> exclude = instances(a, options, "exclude");
        List<Instance> include = instances(a, options, "include");
        if (exclude != null && include != null) throw new HostError("query options cannot have both include and exclude");
        String group = options.get("collisionGroup") instanceof String g ? g : null;
        CollisionGroups groups = group == null ? null : CollisionGroups.of(a.self().tree());
        String tag = options.get("tag") instanceof String t ? t : null;
        ClassDef<?> def = null;
        if (options.get("className") instanceof String className) {
            def = host.classes().find(className);
            if (def == null) throw new HostError("there is no class called %s", className);
        }
        int limit = options.get("limit") instanceof Number n ? Math.max(0, n.intValue()) : Integer.MAX_VALUE;
        boolean respect = Boolean.TRUE.equals(options.get("respectCollides"));
        Queries.Filter filter = include != null
                ? new Queries.Filter(include, true, respect, groups, group, tag, def)
                : new Queries.Filter(exclude != null ? exclude : List.of(), false, respect, groups, group, tag, def);
        return new Params(filter, Boolean.TRUE.equals(options.get("ignoreBlocks")), limit, Boolean.TRUE.equals(options.get("sorted")));
    }

    private static List<Instance> instances(Args a, Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value == null) return null;
        if (value instanceof Map<?, ?> m && m.isEmpty()) return List.of();
        if (!(value instanceof List<?> list)) throw new HostError("%s is a list of instances", key);
        List<Instance> out = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Instance instance)) throw new HostError("%s is a list of instances", key);
            out.add(instance);
        }
        return out;
    }

    private static Hit down(Host host, Instance root, Vector3 from, double depth, Queries.Filter filter) {
        Vector3 way = new Vector3(0, -1, 0);
        Queries.Cast part = Queries.raycast(root, from, way, depth, filter);
        BlockRef blocks = root.parent() == null ? host.blocks() : null;
        BlockRef.Hit block = blocks == null ? null : blocks.raycast(from, way, part == null ? depth : part.distance());
        if (block != null) return new Hit(block.at(), block.normal());
        return part == null ? null : new Hit(part.at(), part.normal());
    }

    private static Object heightmap(Host host, Args a) {
        Instance root = a.self();
        Vector3 p = a.vector(1);
        Vector3 q = a.vector(2);
        double step = Math.max(0.1, a.number(3, 1));
        double x0 = Math.min(p.x(), q.x()), x1 = Math.max(p.x(), q.x());
        double z0 = Math.min(p.z(), q.z()), z1 = Math.max(p.z(), q.z());
        long cells = (long) ((x1 - x0) / step + 1) * (long) ((z1 - z0) / step + 1);
        if (cells > 65536) throw a.error("heightmap of %d samples exceeds the limit of 65536", cells);
        Queries.Filter filter = params(host, a, 4).filter();
        List<Object> rows = new ArrayList<>();
        for (double z = z0; z <= z1 + 1e-9; z += step) {
            List<Object> row = new ArrayList<>();
            for (double x = x0; x <= x1 + 1e-9; x += step) {
                Hit hit = down(host, root, new Vector3(x, SKY, z), DEPTH, filter);
                row.add(hit == null ? null : hit.at().y());
            }
            rows.add(row);
        }
        return rows;
    }

    private static Object freeSpot(Host host, Args a) {
        Instance root = a.self();
        Vector3 centre = a.vector(1);
        Vector3 size = a.vector(2);
        double radius = a.number(3, 16);
        BlockRef blocks = root.parent() == null ? host.blocks() : null;
        Queries.Filter solid = new Queries.Filter(List.of(), false, true);
        double step = Math.max(0.5, Math.min(size.x(), size.z()));
        for (double ring = 0; ring <= radius; ring += step) {
            int around = ring == 0 ? 1 : (int) Math.ceil(2 * Math.PI * ring / step);
            for (int n = 0; n < around; n++) {
                double angle = 2 * Math.PI * n / around;
                double x = centre.x() + Math.cos(angle) * ring;
                double z = centre.z() + Math.sin(angle) * ring;
                Hit ground = down(host, root, new Vector3(x, centre.y() + radius, z), radius * 2, solid);
                if (ground == null) continue;
                Vector3 spot = new Vector3(x, ground.at().y() + size.y() / 2 + 0.01, z);
                if (!Queries.inBox(root, CFrame.at(spot), size, solid).isEmpty()) continue;
                if (blocks != null && blocked(blocks, spot, size)) continue;
                return new Vector3(x, ground.at().y(), z);
            }
        }
        return null;
    }

    private static boolean blocked(BlockRef blocks, Vector3 centre, Vector3 size) {
        int x0 = (int) Math.floor(centre.x() - size.x() / 2), x1 = (int) Math.floor(centre.x() + size.x() / 2 - 1e-6);
        int y0 = (int) Math.floor(centre.y() - size.y() / 2), y1 = (int) Math.floor(centre.y() + size.y() / 2 - 1e-6);
        int z0 = (int) Math.floor(centre.z() - size.z() / 2), z1 = (int) Math.floor(centre.z() + size.z() / 2 - 1e-6);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (blocks.solid(x, y, z)) return true;
                }
            }
        }
        return false;
    }

    private static Vector3 clamp(Vector3 local, Vector3 half) {
        return new Vector3(Math.clamp(local.x(), -half.x(), half.x()), Math.clamp(local.y(), -half.y(), half.y()),
                Math.clamp(local.z(), -half.z(), half.z()));
    }

    private static Object nearest(List<Part> parts, Vector3 at) {
        Part best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Part part : parts) {
            CFrame frame = Transforms.world(part);
            Vector3 local = frame.pointToObject(at);
            double d = local.sub(clamp(local, part.size.mul(0.5))).lengthSq();
            if (d < bestDistance) {
                bestDistance = d;
                best = part;
            }
        }
        return best == null ? null : Results.of(best, Math.sqrt(bestDistance));
    }

    private static void grow(double[] box, Instance instance) {
        if (instance instanceof Part part) {
            Aabb b = SpatialIndex.bounds(Transforms.world(part), part.size);
            box[0] = Math.min(box[0], b.minX());
            box[1] = Math.min(box[1], b.minY());
            box[2] = Math.min(box[2], b.minZ());
            box[3] = Math.max(box[3], b.maxX());
            box[4] = Math.max(box[4], b.maxY());
            box[5] = Math.max(box[5], b.maxZ());
        }
        for (Instance child : instance.children()) grow(box, child);
    }
}
