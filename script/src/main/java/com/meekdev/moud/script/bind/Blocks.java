package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.script.api.BlockRef;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;

// game.blocks: read and change the level's blocks. a position is floored to the block it is inside
public final class Blocks {

    // a fill past this is almost certainly two corners typed wrong, and would stall the tick
    public static final long MOST = 4_000_000;

    private Blocks() {}

    public static void install(LuaState state, BlockRef blocks) {
        state.getGlobal("game");
        state.newTable();
        function(state, "get", s -> {
            Vec3 at = Values.vec3(s, 2);
            s.pushString(blocks.get(floor(at.x()), floor(at.y()), floor(at.z())));
            return 1;
        });
        function(state, "set", s -> {
            writable(s, blocks);
            Vec3 at = Values.vec3(s, 2);
            try {
                blocks.set(floor(at.x()), floor(at.y()), floor(at.z()), s.checkString(3));
            } catch (IllegalArgumentException wrong) {
                throw s.error("%s", wrong.getMessage());
            }
            return 0;
        });
        function(state, "fill", s -> {
            writable(s, blocks);
            Vec3 a = Values.vec3(s, 2);
            Vec3 b = Values.vec3(s, 3);
            int x0 = floor(Math.min(a.x(), b.x())), y0 = floor(Math.min(a.y(), b.y())), z0 = floor(Math.min(a.z(), b.z()));
            int x1 = floor(Math.max(a.x(), b.x())), y1 = floor(Math.max(a.y(), b.y())), z1 = floor(Math.max(a.z(), b.z()));
            long count = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
            if (count > MOST) throw s.error("that fill is %d blocks, and a fill takes at most %d", count, MOST);
            try {
                s.pushNumber(blocks.fill(x0, y0, z0, x1, y1, z1, s.checkString(4)));
            } catch (IllegalArgumentException wrong) {
                throw s.error("%s", wrong.getMessage());
            }
            return 1;
        });
        state.rawSetField(-2, "blocks");
        state.pop(1);
    }

    private static void writable(LuaState state, BlockRef blocks) {
        if (!blocks.writable()) throw state.error("blocks are changed by the server. a client reads them");
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }

    private static void function(LuaState state, String name, ToIntFunction<LuaState> body) {
        state.pushFunction(LuaFunc.wrap(body::applyAsInt, "blocks:" + name));
        state.rawSetField(-2, name);
    }
}
