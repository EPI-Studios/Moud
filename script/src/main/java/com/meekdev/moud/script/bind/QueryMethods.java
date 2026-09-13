package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.instance.CollisionGroups;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.Queries;
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
        return list(state, Queries.inBox(root, Values.cframe(state, 2), Values.vec3(state, 3), params(state, 4, root).filter()));
    }

    static int partsInRadius(LuaState state, Instance root) {
        return list(state, Queries.inRadius(root, Values.vec3(state, 2), state.checkNumber(3), params(state, 4, root).filter()));
    }

    static int partsInPart(LuaState state, Instance root) {
        if (!(state.toUserDataTagged(2, Proxies.TAG) instanceof Part part)) {
            throw state.error("partsInPart wants a part");
        }
        return list(state, Queries.inPart(root, part, params(state, 3, root).filter()));
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

    private record Params(Queries.Filter filter, boolean ignoreBlocks) {}

    // { exclude = { ... } } or { include = { ... } }, respectCollides, collisionGroup, ignoreBlocks
    private static Params params(LuaState state, int at, Instance root) {
        if (state.isNoneOrNil(at)) return new Params(Queries.Filter.ALL, false);
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
        Queries.Filter filter = include != null
                ? new Queries.Filter(include, true, respect, groups, group)
                : new Queries.Filter(exclude != null ? exclude : List.of(), false, respect, groups, group);
        return new Params(filter, ignoreBlocks);
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

    private static boolean flag(LuaState state, int at, String key) {
        state.getField(at, key);
        boolean value = state.toBoolean(-1);
        state.pop(1);
        return value;
    }
}
