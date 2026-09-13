package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.scene.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

final class LuaJson {

    private static final int DEEPEST = 32;

    private LuaJson() {}

    static String read(LuaState state, int at) {
        return Json.write(value(state, state.absIndex(at), 0, "the value"));
    }

    private static Object value(LuaState state, int at, int depth, String where) {
        if (depth > DEEPEST) throw state.error("%s nests deeper than %d", where, DEEPEST);
        LuaType type = state.type(at);
        return switch (type) {
            case NIL, NONE -> null;
            case BOOLEAN -> state.toBoolean(at);
            case NUMBER -> {
                double n = state.toNumber(at);
                if (Double.isNaN(n) || Double.isInfinite(n)) throw state.error("%s is not a number that can be saved", where);
                yield n;
            }
            case STRING -> state.toString(at);
            case TABLE -> table(state, at, depth, where);
            default -> throw state.error("%s is a %s, which cannot be saved. saved data is numbers, text, true or false, and tables of those",
                    where, type.name().toLowerCase());
        };
    }

    private static Object table(LuaState state, int at, int depth, String where) {
        int length = state.len(at);
        if (length > 0) {
            List<Object> list = new ArrayList<>(length);
            for (int n = 1; n <= length; n++) {
                state.rawGetI(at, n);
                list.add(value(state, state.top(), depth + 1, where + "[" + n + "]"));
                state.pop(1);
            }
            return list;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        state.pushNil();
        while (state.next(at)) {
            if (state.type(-2) != LuaType.STRING) throw state.error("%s has a key that is not text", where);
            String key = state.toString(-2);
            map.put(key, value(state, state.top(), depth + 1, where + "." + key));
            state.pop(1);
        }
        return map;
    }

    static void push(LuaState state, String json) {
        if (json == null) {
            state.pushNil();
            return;
        }
        push(state, Json.parse(json));
    }

    private static void push(LuaState state, Object value) {
        switch (value) {
            case null -> state.pushNil();
            case Boolean b -> state.pushBoolean(b);
            case Double d -> state.pushNumber(d);
            case String s -> state.pushString(s);
            case List<?> list -> {
                state.createTable(list.size(), 0);
                for (int n = 0; n < list.size(); n++) {
                    push(state, list.get(n));
                    state.rawSetI(-2, n + 1);
                }
            }
            case Map<?, ?> map -> {
                state.createTable(0, map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    push(state, entry.getValue());
                    state.rawSetField(-2, String.valueOf(entry.getKey()));
                }
            }
            default -> state.pushNil();
        }
    }
}
