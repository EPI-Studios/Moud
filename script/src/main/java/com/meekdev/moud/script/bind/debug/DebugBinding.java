package com.meekdev.moud.script.bind.debug;

import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.query.Queries;
import com.meekdev.moud.script.api.DebugRef;
import com.meekdev.moud.script.bind.LuaTables;
import com.meekdev.moud.script.bind.Plain;
import com.meekdev.moud.script.bind.Profiler;
import com.meekdev.moud.script.bind.Values;
import java.util.Map;
import net.hollowcube.luau.LuaState;

public final class DebugBinding {

    private static final Color DEFAULT = new Color(1, 0.8f, 0.2f, 1);

    private DebugBinding() {}

    public static void install(LuaState state, DebugRef debug) {
        state.getGlobal("game");
        state.newTable();
        LuaTables.function(state, "debug", "drawLine", s -> {
            debug.line(Values.vec3(s, 2), Values.vec3(s, 3), color(s, 4), seconds(s, 5));
            return 0;
        });
        LuaTables.function(state, "debug", "drawRay", s -> {
            Vector3 from = Values.vec3(s, 2);
            debug.line(from, from.add(Values.vec3(s, 3)), color(s, 4), seconds(s, 5));
            return 0;
        });
        LuaTables.function(state, "debug", "drawBox", s -> {
            debug.box(Values.cframe(s, 2), Values.vec3(s, 3), color(s, 4), seconds(s, 5));
            return 0;
        });
        LuaTables.function(state, "debug", "drawSphere", s -> {
            debug.sphere(Values.vec3(s, 2), s.checkNumber(3), color(s, 4), seconds(s, 5));
            return 0;
        });
        LuaTables.function(state, "debug", "drawPoint", s -> {
            debug.sphere(Values.vec3(s, 2), 0.1, color(s, 3), seconds(s, 4));
            return 0;
        });
        LuaTables.function(state, "debug", "label", s -> {
            debug.label(Values.vec3(s, 2), s.checkString(3), color(s, 4), seconds(s, 5));
            return 0;
        });
        LuaTables.function(state, "debug", "watch", s -> {
            debug.watch(s.checkString(2), text(s, 3));
            return 0;
        });
        LuaTables.function(state, "debug", "clear", s -> {
            debug.clear();
            return 0;
        });
        LuaTables.function(state, "debug", "queryStats", s -> {
            Queries.Stats stats = Queries.takeStats();
            Plain.push(s, Map.of("queries", (double) stats.queries(), "partsTested", (double) stats.partsTested()));
            return 1;
        });
        LuaTables.function(state, "debug", "profile", s -> {
            Plain.push(s, Profiler.take(s));
            return 1;
        });
        state.rawSetField(-2, "debug");
        state.pop(1);
    }

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

}
