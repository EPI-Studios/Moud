package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.clazz.CallbackDef;
import com.meekdev.moud.core.event.Callback;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.err.ScriptError;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

public final class Callbacks {

    private record Assigned(Callback callback, int ref) {}

    private static final Map<LuaState, List<Assigned>> ASSIGNED = new HashMap<>();
    private static final Map<LuaState, Consumer<ScriptError>> ERRORS = new HashMap<>();
    private static final Map<Callback, Integer> REFS = new HashMap<>();

    private Callbacks() {}

    public static void install(LuaState state, Consumer<ScriptError> onError) {
        ERRORS.put(state.mainThread(), onError);
    }

    public static void forget(LuaState state) {
        LuaState main = state.mainThread();
        List<Assigned> assigned = ASSIGNED.remove(main);
        if (assigned != null) {
            for (Assigned one : assigned) {
                one.callback().set(null);
                REFS.remove(one.callback());
            }
        }
        ERRORS.remove(main);
    }

    static void push(LuaState state, Instance instance, CallbackDef def) {
        Integer ref = REFS.get(def.on(instance));
        if (ref == null) {
            state.pushNil();
        } else {
            state.getRef(ref);
        }
    }

    static void assign(LuaState state, Instance instance, CallbackDef def, int value) {
        Callback callback = def.on(instance);
        LuaState main = state.mainThread();
        List<Assigned> assigned = ASSIGNED.computeIfAbsent(main, key -> new ArrayList<>());
        Integer old = REFS.remove(callback);
        if (old != null) {
            main.unref(old);
            assigned.removeIf(one -> one.callback() == callback);
        }
        if (state.isNoneOrNil(value)) {
            callback.set(null);
            return;
        }
        if (state.type(value) != LuaType.FUNCTION) {
            throw state.error("%s.%s expects a function or nil", instance.def().name(), def.name());
        }
        state.pushValue(value);
        int ref = state.ref(-1);
        state.pop(1);
        REFS.put(callback, ref);
        assigned.add(new Assigned(callback, ref));
        String where = instance.def().name() + "." + def.name();
        callback.set(args -> call(main, ref, where, args));
    }

    private static Object[] call(LuaState state, int ref, String where, Object[] args) {
        int base = state.top();
        long started = System.nanoTime();
        state.getRef(ref);
        for (Object arg : args) Plain.push(state, arg);
        try {
            state.call(args.length, -1);
            int count = state.top() - base;
            Object[] out = new Object[count];
            for (int n = 0; n < count; n++) out[n] = Plain.read(state, base + 1 + n);
            return out;
        } catch (RuntimeException e) {
            Consumer<ScriptError> onError = ERRORS.get(state);
            if (onError != null) onError.accept(new ScriptError(where, e.getMessage(), e));
            return null;
        } finally {
            state.top(base);
            Profiler.add(state, where, System.nanoTime() - started);
        }
    }
}
