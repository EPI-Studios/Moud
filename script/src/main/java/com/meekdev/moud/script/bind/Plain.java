package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

public final class Plain {

    private Plain() {}

    public static Object read(LuaState state, int at) {
        return one(state, at, 0);
    }

    private static Object one(LuaState state, int at, int depth) {
        if (depth > 16) throw state.error("a table nested that deep is not a message");
        LuaType type = state.type(at);
        return switch (type) {
            case NIL, NONE -> null;
            case BOOLEAN -> state.toBoolean(at);
            case NUMBER -> state.toNumber(at);
            case STRING -> state.toString(at);
            case TABLE -> table(state, at, depth);
            case USERDATA -> userdata(state, at);
            default -> throw state.error("a %s cannot be sent", type.name().toLowerCase());
        };
    }

    private static Object userdata(LuaState state, int at) {
        Object value = Values.value(state, at);
        if (value != null) return value;
        Object instance = state.toUserDataTagged(at, Proxies.TAG);
        if (instance != null) return instance;
        throw state.error("that is not something that can be sent");
    }

    private static Object table(LuaState state, int at, int depth) {
        int length = state.len(at);
        if (length > 0) {
            List<Object> list = new ArrayList<>(length);
            for (int n = 1; n <= length; n++) {
                state.rawGetI(at, n);
                list.add(one(state, state.top(), depth + 1));
                state.pop(1);
            }
            return list;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        state.pushNil();
        while (state.next(at < 0 ? at - 1 : at)) {
            if (state.type(-2) != LuaType.STRING) {
                throw state.error("a table that is sent is a list or is keyed by text");
            }
            map.put(state.toString(-2), one(state, state.top(), depth + 1));
            state.pop(1);
        }
        return map;
    }

    public static void push(LuaState state, Object value) {
        switch (value) {
            case null -> state.pushNil();
            case Boolean b -> state.pushBoolean(b);
            case Number d -> state.pushNumber(d.doubleValue());
            case String s -> state.pushString(s);
            case Vector3 v -> Values.push(state, v);
            case Quat q -> Values.push(state, q);
            case CFrame c -> Values.push(state, c);
            case Color c -> Values.push(state, c);
            case UDim2 u -> Values.push(state, u);
            case Instance i -> Proxies.push(state, i);
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
