package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.query.Queries;
import com.meekdev.moud.core.query.SpatialIndex;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Aabb;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.script.api.BlockRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

public final class WorldMethods {

    private static final double SKY = 320;
    private static final double DEPTH = 400;

    private WorldMethods() {}

    public static void install(LuaState state) {
        Proxies.extraMethod(state, "nearestPart", s -> {
            Instance root = self(s);
            Vector3 at = Values.vec3(s, 2);
            double radius = s.checkNumber(3);
            Queries.Filter filter = QueryMethods.filter(s, 4, root);
            return nearest(s, Queries.inRadius(root, at, radius, filter), at);
        });
        Proxies.extraMethod(state, "nearestTagged", s -> {
            Instance root = self(s);
            Vector3 at = Values.vec3(s, 2);
            String tag = s.checkString(3);
            double radius = s.isNoneOrNil(4) ? 256 : s.checkNumber(4);
            Queries.Filter filter = new Queries.Filter(List.of(), false, false, null, null, tag, null);
            return nearest(s, Queries.inRadius(root, at, radius, filter), at);
        });
        Proxies.extraMethod(state, "partsAlongRay", s -> QueryMethods.raycastAll(s, self(s)));
        Proxies.extraMethod(state, "capsulecast", s -> {
            Instance root = self(s);
            Vector3 from = Values.vec3(s, 2);
            Vector3 to = Values.vec3(s, 3);
            double radius = s.checkNumber(4);
            Queries.Cast hit = Queries.spherecast(root, from, radius, to.sub(from), to.sub(from).length(),
                    QueryMethods.filter(s, 5, root));
            return QueryMethods.pushCast(s, hit);
        });
        Proxies.extraMethod(state, "sweep", s -> {
            Instance root = self(s);
            Part part = part(s, 2);
            Vector3 direction = Values.vec3(s, 3);
            double distance = s.checkNumber(4);
            Queries.Filter base = QueryMethods.filter(s, 5, root);
            List<Instance> ignore = new ArrayList<>(base.include() ? List.of() : base.instances());
            ignore.add(part);
            Queries.Filter filter = new Queries.Filter(ignore, false, base.respectCollides(), base.groups(), base.group(),
                    base.tag(), base.className());
            return QueryMethods.pushCast(s, Queries.blockcast(root, Transforms.world(part), part.size, direction, distance, filter));
        });
        Proxies.extraMethod(state, "partsAtPoint", s -> {
            Instance root = self(s);
            Vector3 at = Values.vec3(s, 2);
            List<Part> found = Queries.inRadius(root, at, 0, QueryMethods.filter(s, 3, root));
            found.removeIf(p -> !PlayerQueries.inside(Transforms.world(p), p.size, at));
            return QueryMethods.list(s, found);
        });
        Proxies.extraMethod(state, "boundsOf", s -> {
            if (s.type(2) != LuaType.TABLE) throw s.error("boundsOf wants a list of instances");
            double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (int n = 1; n <= s.len(2); n++) {
                s.rawGetI(2, n);
                if (s.toUserDataTagged(-1, Proxies.TAG) instanceof Instance instance) grow(box, instance);
                s.pop(1);
            }
            if (box[0] > box[3]) {
                s.pushNil();
                return 1;
            }
            Values.push(s, CFrame.at((box[0] + box[3]) / 2, (box[1] + box[4]) / 2, (box[2] + box[5]) / 2));
            Values.push(s, new Vector3(box[3] - box[0], box[4] - box[1], box[5] - box[2]));
            return 2;
        });
        Proxies.extraMethod(state, "groundAt", s -> {
            Instance root = self(s);
            double x = s.checkNumber(2);
            double z = s.checkNumber(3);
            double from = s.isNoneOrNil(4) ? SKY : s.checkNumber(4);
            Hit hit = down(s, root, new Vector3(x, from, z), DEPTH, QueryMethods.filter(s, 5, root));
            if (hit == null) {
                s.pushNil();
                return 1;
            }
            s.pushNumber(hit.at().y());
            Values.push(s, hit.normal());
            return 2;
        });
        Proxies.extraMethod(state, "surfaceNormal", s -> {
            Instance root = self(s);
            Vector3 at = Values.vec3(s, 2);
            Hit hit = down(s, root, at.add(new Vector3(0, 1, 0)), 3, QueryMethods.filter(s, 3, root));
            if (hit == null) {
                s.pushNil();
                return 1;
            }
            Values.push(s, hit.normal());
            return 1;
        });
        Proxies.extraMethod(state, "heightmap", s -> {
            Instance root = self(s);
            Vector3 a = Values.vec3(s, 2);
            Vector3 b = Values.vec3(s, 3);
            double step = s.isNoneOrNil(4) ? 1 : Math.max(0.1, s.checkNumber(4));
            double x0 = Math.min(a.x(), b.x()), x1 = Math.max(a.x(), b.x());
            double z0 = Math.min(a.z(), b.z()), z1 = Math.max(a.z(), b.z());
            long cells = (long) ((x1 - x0) / step + 1) * (long) ((z1 - z0) / step + 1);
            if (cells > 65536) throw s.error("a heightmap takes at most 65536 samples, and that is %d", cells);
            Queries.Filter filter = QueryMethods.filter(s, 5, root);
            s.createTable(0, 0);
            int row = 1;
            for (double z = z0; z <= z1 + 1e-9; z += step, row++) {
                s.createTable(0, 0);
                int column = 1;
                for (double x = x0; x <= x1 + 1e-9; x += step, column++) {
                    Hit hit = down(s, root, new Vector3(x, SKY, z), DEPTH, filter);
                    if (hit != null) {
                        s.pushNumber(hit.at().y());
                        s.rawSetI(-2, column);
                    }
                }
                s.rawSetI(-2, row);
            }
            return 1;
        });
        Proxies.extraMethod(state, "findFreeSpot", s -> {
            Instance root = self(s);
            Vector3 centre = Values.vec3(s, 2);
            Vector3 size = Values.vec3(s, 3);
            double radius = s.isNoneOrNil(4) ? 16 : s.checkNumber(4);
            BlockRef blocks = root.parent() == null ? QueryMethods.blocksOf(s) : null;
            Queries.Filter solid = new Queries.Filter(List.of(), false, true);
            double step = Math.max(0.5, Math.min(size.x(), size.z()));
            for (double ring = 0; ring <= radius; ring += step) {
                int around = ring == 0 ? 1 : (int) Math.ceil(2 * Math.PI * ring / step);
                for (int n = 0; n < around; n++) {
                    double angle = 2 * Math.PI * n / around;
                    double x = centre.x() + Math.cos(angle) * ring;
                    double z = centre.z() + Math.sin(angle) * ring;
                    Hit ground = down(s, root, new Vector3(x, centre.y() + radius, z), radius * 2, solid);
                    if (ground == null) continue;
                    Vector3 spot = new Vector3(x, ground.at().y() + size.y() / 2 + 0.01, z);
                    if (!Queries.inBox(root, CFrame.at(spot), size, solid).isEmpty()) continue;
                    if (blocks != null && blocked(blocks, spot, size)) continue;
                    Values.push(s, new Vector3(x, ground.at().y(), z));
                    return 1;
                }
            }
            s.pushNil();
            return 1;
        });

        Map<String, ToIntFunction<LuaState>> parts = new LinkedHashMap<>();
        parts.put("overlapping", s -> {
            Part part = part(s, 1);
            Instance root = part.tree().root();
            return QueryMethods.list(s, Queries.inPart(root, part, QueryMethods.filter(s, 2, root)));
        });
        parts.put("closestPoint", s -> {
            Part part = part(s, 1);
            Vector3 at = Values.vec3(s, 2);
            CFrame frame = Transforms.world(part);
            Vector3 local = frame.pointToObject(at);
            Vector3 half = part.size.mul(0.5);
            Vector3 clamped = new Vector3(Math.clamp(local.x(), -half.x(), half.x()), Math.clamp(local.y(), -half.y(), half.y()),
                    Math.clamp(local.z(), -half.z(), half.z()));
            Values.push(s, frame.pointToWorld(clamped));
            return 1;
        });
        parts.put("contains", s -> {
            Part part = part(s, 1);
            s.pushBoolean(PlayerQueries.inside(Transforms.world(part), part.size, Values.vec3(s, 2)));
            return 1;
        });
        parts.put("bounds", s -> {
            Part part = part(s, 1);
            Values.push(s, Transforms.world(part));
            Values.push(s, part.size);
            return 2;
        });
        parts.put("worldBounds", s -> {
            Part part = part(s, 1);
            Aabb box = SpatialIndex.bounds(Transforms.world(part), part.size);
            Values.push(s, new Vector3(box.minX(), box.minY(), box.minZ()));
            Values.push(s, new Vector3(box.maxX(), box.maxY(), box.maxZ()));
            return 2;
        });
        Proxies.classMethods(state, Classes.PART, parts);
    }

    private record Hit(Vector3 at, Vector3 normal) {}

    private static Hit down(LuaState s, Instance root, Vector3 from, double depth, Queries.Filter filter) {
        Vector3 way = new Vector3(0, -1, 0);
        Queries.Cast part = Queries.raycast(root, from, way, depth, filter);
        BlockRef blocks = root.parent() == null ? QueryMethods.blocksOf(s) : null;
        BlockRef.Hit block = blocks == null ? null : blocks.raycast(from, way, part == null ? depth : part.distance());
        if (block != null) return new Hit(block.at(), block.normal());
        return part == null ? null : new Hit(part.at(), part.normal());
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

    private static int nearest(LuaState s, List<Part> parts, Vector3 at) {
        Part best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Part part : parts) {
            CFrame frame = Transforms.world(part);
            Vector3 local = frame.pointToObject(at);
            Vector3 half = part.size.mul(0.5);
            Vector3 clamped = new Vector3(Math.clamp(local.x(), -half.x(), half.x()), Math.clamp(local.y(), -half.y(), half.y()),
                    Math.clamp(local.z(), -half.z(), half.z()));
            double d = local.sub(clamped).lengthSq();
            if (d < bestDistance) {
                bestDistance = d;
                best = part;
            }
        }
        if (best == null) {
            s.pushNil();
            return 1;
        }
        Proxies.push(s, best);
        s.pushNumber(Math.sqrt(bestDistance));
        return 2;
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

    private static Instance self(LuaState s) {
        if (!(s.toUserDataTagged(1, Proxies.TAG) instanceof Instance instance)) throw s.error("not an instance");
        return instance;
    }

    private static Part part(LuaState s, int at) {
        if (!(s.toUserDataTagged(at, Proxies.TAG) instanceof Part part)) throw s.error("wants a part");
        return part;
    }
}
