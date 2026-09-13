package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.instance.Queries;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.script.api.DebugRef;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;

// game.debug: shapes and labels drawn for a while, watched values, what queries cost, and a profile
public final class DebugBinding {

    private static final Color DEFAULT = new Color(1, 0.8f, 0.2f, 1);

    private DebugBinding() {}

    public static void install(LuaState state, DebugRef debug) {
        state.getGlobal("game");
        state.newTable();
        function(state, "drawLine", s -> {
            debug.line(Values.vec3(s, 2), Values.vec3(s, 3), color(s, 4), seconds(s, 5));
            return 0;
        });
        function(state, "drawRay", s -> {
            Vec3 from = Values.vec3(s, 2);
            debug.line(from, from.add(Values.vec3(s, 3)), color(s, 4), seconds(s, 5));
            return 0;
        });
        function(state, "drawBox", s -> {
            debug.box(Values.cframe(s, 2), Values.vec3(s, 3), color(s, 4), seconds(s, 5));
            return 0;
        });
        function(state, "drawSphere", s -> {
            debug.sphere(Values.vec3(s, 2), s.checkNumber(3), color(s, 4), seconds(s, 5));
            return 0;
        });
        function(state, "drawPoint", s -> {
            debug.sphere(Values.vec3(s, 2), 0.1, color(s, 3), seconds(s, 4));
            return 0;
        });
        function(state, "label", s -> {
            debug.label(Values.vec3(s, 2), s.checkString(3), color(s, 4), seconds(s, 5));
            return 0;
        });
        function(state, "watch", s -> {
            debug.watch(s.checkString(2), text(s, 3));
            return 0;
        });
        function(state, "clear", s -> {
            debug.clear();
            return 0;
        });
        function(state, "queryStats", s -> {
            long[] stats = Queries.takeStats();
            Plain.push(s, Map.of("queries", (double) stats[0], "partsTested", (double) stats[1]));
            return 1;
        });
        function(state, "profile", s -> {
            Plain.push(s, Profiler.take(s));
            return 1;
        });
        state.rawSetField(-2, "debug");
        state.pop(1);
    }

    // a value the way luau prints it: 20 rather than 20.0
    private static String text(LuaState s, int at) {
        if (s.isNoneOrNil(at)) return "nil";
        if (s.isNumber(at)) {
            double n = s.toNumber(at);
            return n == Math.rint(n) && Math.abs(n) < 1e15 ? String.valueOf((long) n) : String.valueOf(n);
        }
        Object value = Plain.read(s, at);
        return String.valueOf(value);
    }

    private static Color color(LuaState s, int at) {
        Object value = s.isNoneOrNil(at) ? null : Values.value(s, at);
        return value instanceof Color c ? c : DEFAULT;
    }

    private static double seconds(LuaState s, int at) {
        return s.isNoneOrNil(at) ? 0 : s.checkNumber(at);
    }

    private static void function(LuaState state, String name, ToIntFunction<LuaState> body) {
        state.pushFunction(LuaFunc.wrap(body::applyAsInt, "debug:" + name));
        state.rawSetField(-2, name);
    }
}
