package com.meekdev.moud.script.reload;

import com.meekdev.moud.script.bind.Values;
import java.util.LinkedHashMap;
import java.util.Map;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

// game.persist crosses a reload as plain data, because the new vm shares nothing with the old one.
// scalars, the value types and nested tables travel; functions and instances deliberately do not
public final class Persist {

    private static final int MAX_DEPTH = 16;

    private Persist() {}

    public static Map<String, Object> capture(LuaState state) {
        state.getGlobal("game");
        if (state.rawGetField(-1, "persist") != LuaType.TABLE) {
            state.pop(2);
            return Map.of();
        }
        Map<String, Object> data = table(state, 0);
        state.pop(2);
        return data;
    }

    public static void restore(LuaState state, Map<String, Object> data) {
        state.getGlobal("game");
        state.newTable();
        write(state, data);
        state.rawSetField(-2, "persist");
        state.pop(1);
    }

    private static Map<String, Object> table(LuaState state, int depth) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (depth >= MAX_DEPTH) return out;
        state.pushNil();
        while (state.next(state.absIndex(-2))) {
            String key = state.type(-2) == LuaType.STRING || state.type(-2) == LuaType.NUMBER
                    ? state.toStringRepr(-2) : null;
            if (key != null) {
                Object value = read(state, depth);
                if (value != null) out.put(key, value);
            }
            state.pop(1);
        }
        return out;
    }

    private static Object read(LuaState state, int depth) {
        return switch (state.type(-1)) {
            case NUMBER -> state.toNumber(-1);
            case BOOLEAN -> state.toBoolean(-1);
            case STRING -> state.toString(-1);
            case TABLE -> table(state, depth + 1);
            case USERDATA -> Values.value(state, -1);
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static void write(LuaState state, Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();
            switch (value) {
                case Double d -> state.pushNumber(d);
                case Boolean b -> state.pushBoolean(b);
                case String s -> state.pushString(s);
                case Map<?, ?> m -> {
                    state.newTable();
                    write(state, (Map<String, Object>) m);
                }
                default -> {
                    if (!Values.push(state, value)) continue;
                }
            }
            state.rawSetField(-2, entry.getKey());
        }
    }
}
