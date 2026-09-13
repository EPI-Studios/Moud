package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.instance.CollisionGroups;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.Queries;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.script.api.BlockRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

// casts and overlaps, asked of any instance and answered from under it. blocks are only in the answer
// when it is asked of the world
final class QueryMethods {

    private static final Map<LuaState, BlockRef> BLOCKS = new HashMap<>();

    private QueryMethods() {}

    static void blocks(LuaState state, BlockRef blocks) {
        BLOCKS.put(state.mainThread(), blocks);
    }

    // whether nothing solid stands between two points: no part that collides, and no block when the
    // question is asked of the whole world. the ignored instances count with everything under them
    static boolean clear(LuaState state, Instance root, Vec3 from, Vec3 to, List<Instance> ignore) {
        Vec3 way = to.sub(from);
        double length = way.length();
        if (length < 1e-6) return true;
        Queries.Filter filter = new Queries.Filter(ignore, false, true);
        if (Queries.raycast(root, from, way, length, filter) != null) return false;
        BlockRef blocks = BLOCKS.get(state.mainThread());
        return blocks == null || root.parent() != null || blocks.raycast(from, way, length) == null;
    }

    static BlockRef blocksOf(LuaState state) {
        return BLOCKS.get(state.mainThread());
    }

    static void forget(LuaState state) {
        BLOCKS.remove(state.mainThread());
    }

    // root, from, direction, range, params -> part, position, distance, normal, block
    static int raycast(LuaState state, Instance root) {
        Vec3 from = Values.vec3(state, 2);
        Vec3 direction = Values.vec3(state, 3);
        double range = state.isNoneOrNil(4) ? 100 : state.checkNumber(4);
        Params params = params(state, 5, root);
        Queries.Cast hit = Queries.raycast(root, from, direction, range, params.filter());
        BlockRef blocks = BLOCKS.get(state.mainThread());
        if (!params.ignoreBlocks() && blocks != null && root.parent() == null) {
            BlockRef.Hit block = blocks.raycast(from, direction, hit == null ? range : hit.distance());
            if (block != null && (hit == null || block.distance() < hit.distance())) {
                state.pushNil();
                Values.push(state, block.at());
                state.pushNumber(block.distance());
                Values.push(state, block.normal());
                state.pushString(block.block());
                return 5;
            }
        }
        return push(state, hit);
    }

    static int spherecast(LuaState state, Instance root) {
        Vec3 from = Values.vec3(state, 2);
        double radius = state.checkNumber(3);
        Vec3 direction = Values.vec3(state, 4);
        double range = state.isNoneOrNil(5) ? 100 : state.checkNumber(5);
        return push(state, Queries.spherecast(root, from, radius, direction, range, params(state, 6, root).filter()));
    }

    static int blockcast(LuaState state, Instance root) {
        CFrame frame = Values.cframe(state, 2);
        Vec3 size = Values.vec3(state, 3);
        Vec3 direction = Values.vec3(state, 4);
        double range = state.isNoneOrNil(5) ? 100 : state.checkNumber(5);
        return push(state, Queries.blockcast(root, frame, size, direction, range, params(state, 6, root).filter()));
    }

    static int partsInBox(LuaState state, Instance root) {
        CFrame frame = Values.cframe(state, 2);
        Params params = params(state, 4, root);
        return list(state, shape(Queries.inBox(root, frame, Values.vec3(state, 3), params.filter()), frame.position(), params));
    }

    static int partsInRadius(LuaState state, Instance root) {
        Vec3 centre = Values.vec3(state, 2);
        Params params = params(state, 4, root);
        return list(state, shape(Queries.inRadius(root, centre, state.checkNumber(3), params.filter()), centre, params));
    }

    // nearest first when asked, and no more than the limit
    private static List<Part> shape(List<Part> parts, Vec3 from, Params params) {
        if (params.sorted()) {
            parts.sort((a, b) -> Double.compare(Transforms.world(a).position().sub(from).lengthSq(),
                    Transforms.world(b).position().sub(from).lengthSq()));
        }
        return parts.size() > params.limit() ? new ArrayList<>(parts.subList(0, params.limit())) : parts;
    }

    // every part a ray passes into, nearest first: { { part, position, distance, normal }, ... }
    static int raycastAll(LuaState state, Instance root) {
        Vec3 from = Values.vec3(state, 2);
        Vec3 direction = Values.vec3(state, 3);
        double range = state.isNoneOrNil(4) ? 100 : state.checkNumber(4);
        Params params = params(state, 5, root);
        List<Queries.Cast> hits = Queries.raycastAll(root, from, direction, range, params.filter());
        int count = Math.min(hits.size(), params.limit());
        state.createTable(count, 0);
        for (int n = 0; n < count; n++) {
            hit(state, hits.get(n));
            state.rawSetI(-2, n + 1);
        }
        return 1;
    }

    // many rays in one call: { { from, direction, range? }, ... } -> a list of hit tables, false for a miss
    static int raycastMany(LuaState state, Instance root) {
        if (state.type(2) != LuaType.TABLE) throw state.error("raycastMany wants a list of { from, direction, range }");
        Params params = params(state, 3, root);
        int count = state.len(2);
        state.createTable(count, 0);
        int out = state.top();
        for (int n = 1; n <= count; n++) {
            state.rawGetI(2, n);
            int ray = state.top();
            state.rawGetI(ray, 1);
            Vec3 from = Values.vec3(state, -1);
            state.pop(1);
            state.rawGetI(ray, 2);
            Vec3 direction = Values.vec3(state, -1);
            state.pop(1);
            state.rawGetI(ray, 3);
            double range = state.isNumber(-1) ? state.toNumber(-1) : 100;
            state.pop(2);
            Queries.Cast cast = Queries.raycast(root, from, direction, range, params.filter());
            if (cast == null) {
                state.pushBoolean(false);
            } else {
                hit(state, cast);
            }
            state.rawSetI(out, n);
        }
        return 1;
    }

    private static void hit(LuaState state, Queries.Cast cast) {
        state.createTable(0, 4);
        Proxies.push(state, cast.part());
        state.rawSetField(-2, "part");
        Values.push(state, cast.at());
        state.rawSetField(-2, "position");
        state.pushNumber(cast.distance());
        state.rawSetField(-2, "distance");
        Values.push(state, cast.normal());
        state.rawSetField(-2, "normal");
    }

    static int partsInPart(LuaState state, Instance root) {
        if (!(state.toUserDataTagged(2, Proxies.TAG) instanceof Part part)) {
            throw state.error("partsInPart wants a part");
        }
        Params params = params(state, 3, root);
        return list(state, shape(Queries.inPart(root, part, params.filter()), Transforms.world(part).position(), params));
    }

    private static int push(LuaState state, Queries.Cast hit) {
        if (hit == null) {
            state.pushNil();
            return 1;
        }
        Proxies.push(state, hit.part());
        Values.push(state, hit.at());
        state.pushNumber(hit.distance());
        Values.push(state, hit.normal());
        return 4;
    }

    private static int list(LuaState state, List<Part> parts) {
        state.createTable(parts.size(), 0);
        for (int n = 0; n < parts.size(); n++) {
            Proxies.push(state, parts.get(n));
            state.rawSetI(-2, n + 1);
        }
        return 1;
    }

    private record Params(Queries.Filter filter, boolean ignoreBlocks, int limit, boolean sorted) {

        static final Params NONE = new Params(Queries.Filter.ALL, false, Integer.MAX_VALUE, false);
    }

    // { exclude = { ... } } or { include = { ... } }, respectCollides, collisionGroup, ignoreBlocks
    private static Params params(LuaState state, int at, Instance root) {
        if (state.isNoneOrNil(at)) return Params.NONE;
        if (state.type(at) != LuaType.TABLE) throw state.error("query options are a table");
        List<Instance> exclude = instances(state, at, "exclude");
        List<Instance> include = instances(state, at, "include");
        if (exclude != null && include != null) {
            throw state.error("a query takes include or exclude, not both");
        }
        boolean respect = flag(state, at, "respectCollides");
        boolean ignoreBlocks = flag(state, at, "ignoreBlocks");
        String group = null;
        state.getField(at, "collisionGroup");
        if (!state.isNil(-1)) group = state.checkString(-1);
        state.pop(1);
        CollisionGroups groups = group == null ? null : CollisionGroups.of(root.tree());
        String tag = text(state, at, "tag");
        String className = text(state, at, "className");
        ClassDef<?> def = null;
        if (className != null) {
            def = Proxies.registry().find(className);
            if (def == null) throw state.error("there is no class called %s", className);
        }
        state.getField(at, "limit");
        int limit = state.isNumber(-1) ? Math.max(0, (int) state.toNumber(-1)) : Integer.MAX_VALUE;
        state.pop(1);
        boolean sorted = flag(state, at, "sorted");
        Queries.Filter filter = include != null
                ? new Queries.Filter(include, true, respect, groups, group, tag, def)
                : new Queries.Filter(exclude != null ? exclude : List.of(), false, respect, groups, group, tag, def);
        return new Params(filter, ignoreBlocks, limit, sorted);
    }

    private static List<Instance> instances(LuaState state, int at, String key) {
        state.getField(at, key);
        if (state.isNoneOrNil(-1)) {
            state.pop(1);
            return null;
        }
        if (state.type(-1) != LuaType.TABLE) throw state.error("%s is a list of instances", key);
        int table = state.top();
        List<Instance> out = new ArrayList<>();
        int length = state.len(table);
        for (int n = 1; n <= length; n++) {
            state.rawGetI(table, n);
            if (!(state.toUserDataTagged(-1, Proxies.TAG) instanceof Instance instance)) {
                throw state.error("%s is a list of instances", key);
            }
            out.add(instance);
            state.pop(1);
        }
        state.pop(1);
        return out;
    }

    private static String text(LuaState state, int at, String key) {
        state.getField(at, key);
        String value = state.isNil(-1) ? null : state.checkString(-1);
        state.pop(1);
        return value;
    }

    private static boolean flag(LuaState state, int at, String key) {
        state.getField(at, key);
        boolean value = state.toBoolean(-1);
        state.pop(1);
        return value;
    }
}
