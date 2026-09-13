package com.meekdev.moud.script.bind.java;

import com.meekdev.moud.script.err.ScriptError;
import com.meekdev.moud.script.mixin.Dispatch;
import com.meekdev.moud.script.mixin.Injections;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

public final class Mixins {

    static final int CALL = 22;
    static final int HANDLE = 23;

    private record Placed(Method method, Dispatch.Hook hook) {}

    private record Handle(LuaState state, List<Placed> placed, int ref) {}

    private static final Map<LuaState, List<Handle>> HANDLES = new HashMap<>();

    private Mixins() {}

    public static void install(LuaState state, Consumer<ScriptError> onError) {
        state.newTable();
        state.pushFunction(LuaFunc.wrap(Mixins::callIndex, "MixinCall.__index"));
        state.rawSetField(-2, "__index");
        state.setUserDataMetaTable(CALL);

        state.newTable();
        state.pushFunction(LuaFunc.wrap(Mixins::handleIndex, "Mixin.__index"));
        state.rawSetField(-2, "__index");
        state.setUserDataMetaTable(HANDLE);

        methods(state);

        state.newTable();
        state.pushFunction(LuaFunc.wrap(s -> inject(s, onError), "mixin.inject"));
        state.rawSetField(-2, "inject");
        state.setGlobal("mixin");
    }

    private static int inject(LuaState state, Consumer<ScriptError> onError) {
        String className = state.checkString(1);
        String name = state.checkString(2);
        String at = state.checkString(3);
        if (!state.isFunction(4)) throw state.error("mixin.inject wants a function to run, as the fourth argument");
        boolean head = switch (at) {
            case "head" -> true;
            case "return", "tail" -> false;
            default -> throw state.error("'%s' is not a place in a method: head or return", at);
        };
        String descriptor = null;
        int params = -1;
        if (state.type(5) == LuaType.TABLE) {
            state.getField(5, "descriptor");
            if (state.isString(-1)) descriptor = state.toString(-1);
            state.pop(1);
            state.getField(5, "params");
            if (state.isNumber(-1)) params = (int) state.toNumber(-1);
            state.pop(1);
        }

        Class<?> type = Java.load(state, className);
        List<Method> matched = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.isBridge() || method.isSynthetic()) continue;
            if (params >= 0 && method.getParameterCount() != params) continue;
            if (descriptor != null && !Injections.id(method).endsWith(descriptor)) continue;
            matched.add(method);
        }
        if (matched.isEmpty()) {
            List<String> near = new ArrayList<>();
            for (Class<?> c = type.getSuperclass(); c != null; c = c.getSuperclass()) {
                for (Method method : c.getDeclaredMethods()) {
                    if (method.getName().equals(name)) near.add(c.getName());
                }
            }
            throw state.error(near.isEmpty()
                    ? "%s declares no method '%s'"
                    : "%s declares no method '%s', it inherits it: hook " + String.join(" or ", near) + " instead",
                    className, name);
        }

        LuaState main = state.mainThread();
        state.pushValue(4);
        int ref = state.ref(-1);
        state.pop(1);
        Thread owner = Thread.currentThread();
        List<Placed> placed = new ArrayList<>();
        for (Method method : matched) {
            Dispatch.Hook hook = hook(main, ref, owner, method, onError);
            try {
                Injections.add(method, hook, head);
            } catch (RuntimeException failed) {
                for (Placed done : placed) Injections.remove(done.method(), done.hook());
                state.unref(ref);
                throw state.error("could not hook %s: %s", method, failed.getMessage());
            }
            placed.add(new Placed(method, hook));
        }
        Handle handle = new Handle(main, placed, ref);
        HANDLES.computeIfAbsent(main, k -> new ArrayList<>()).add(handle);
        state.newUserDataTaggedWithMetatable(handle, HANDLE);
        return 1;
    }

    private static Dispatch.Hook hook(LuaState state, int ref, Thread owner, Method method, Consumer<ScriptError> onError) {
        String where = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        return new Dispatch.Hook() {
            @Override
            public Thread owner() {
                return owner;
            }

            @Override
            public void run(Dispatch.Call call) {
                int top = state.top();
                try {
                    if (state.getRef(ref) != LuaType.FUNCTION) return;
                    Java.push(state, call.self);
                    state.newUserDataTaggedWithMetatable(call, CALL);
                    for (Object arg : call.args) Java.push(state, arg);
                    state.call(2 + call.args.length, 0);
                } catch (RuntimeException broken) {
                    onError.accept(new ScriptError("mixin " + where, broken.getMessage(), broken));
                } finally {
                    state.top(top);
                }
            }
        };
    }

    private static Dispatch.Call call(LuaState state) {
        if (!(state.toUserDataTagged(1, CALL) instanceof Dispatch.Call call)) throw state.error("not a mixin call");
        return call;
    }

    private static final String CALL_METHODS = "__moud_mixin_call";
    private static final String HANDLE_METHODS = "__moud_mixin_handle";

    private static void methods(LuaState state) {
        state.newTable();
        method(state, "cancel", s -> {
            Dispatch.Call c = call(s);
            if (c.returning) throw s.error("the method already ran: at return, change what it gives with setReturn");
            c.cancel(s.isNoneOrNil(2) ? null : value(s, 2, c.returns));
            return 0;
        });
        method(state, "setReturn", s -> {
            Dispatch.Call c = call(s);
            if (c.returns == void.class) throw s.error("this method returns nothing");
            c.value(value(s, 2, c.returns));
            return 0;
        });
        method(state, "getReturn", s -> {
            Java.push(s, call(s).value());
            return 1;
        });
        method(state, "getArg", s -> {
            Dispatch.Call c = call(s);
            Java.push(s, c.args[slot(s, c)]);
            return 1;
        });
        method(state, "setArg", s -> {
            Dispatch.Call c = call(s);
            if (c.returning) throw s.error("the method already ran with its arguments");
            int n = slot(s, c);
            c.args[n] = value(s, 3, c.parameters[n]);
            return 0;
        });
        state.rawSetField(LuaState.REGISTRY_INDEX, CALL_METHODS);

        state.newTable();
        method(state, "remove", s -> {
            if (s.toUserDataTagged(1, HANDLE) instanceof Handle h) remove(h);
            return 0;
        });
        state.rawSetField(LuaState.REGISTRY_INDEX, HANDLE_METHODS);
    }

    private static void method(LuaState state, String name, ToIntFunction<LuaState> body) {
        state.pushFunction(LuaFunc.wrap(body, "mixin:" + name));
        state.rawSetField(-2, name);
    }

    private static int callIndex(LuaState state) {
        Dispatch.Call call = call(state);
        String key = state.checkString(2);
        switch (key) {
            case "returning" -> state.pushBoolean(call.returning);
            case "cancelled" -> state.pushBoolean(call.cancelled());
            default -> {
                state.rawGetField(LuaState.REGISTRY_INDEX, CALL_METHODS);
                if (state.rawGetField(-1, key) == LuaType.NIL) {
                    throw state.error("a mixin call has no '%s': cancel, setReturn, getReturn, getArg, setArg, returning, cancelled", key);
                }
                state.remove(-2);
            }
        }
        return 1;
    }

    private static int slot(LuaState state, Dispatch.Call call) {
        int n = (int) state.checkNumber(2) - 1;
        if (n < 0 || n >= call.args.length) throw state.error("the method takes %d arguments, there is no %d", call.args.length, n + 1);
        return n;
    }

    private static Object value(LuaState state, int at, Class<?> type) {
        Object value = Java.convert(state, at, type);
        if (value == Java.MISMATCH) throw state.error("that can not be a %s", type.getSimpleName());
        return value;
    }

    private static int handleIndex(LuaState state) {
        if (!(state.toUserDataTagged(1, HANDLE) instanceof Handle handle)) throw state.error("not a mixin");
        String key = state.checkString(2);
        if (key.equals("methods")) {
            state.pushNumber(handle.placed().size());
            return 1;
        }
        state.rawGetField(LuaState.REGISTRY_INDEX, HANDLE_METHODS);
        if (state.rawGetField(-1, key) == LuaType.NIL) throw state.error("a mixin has no '%s': remove, methods", key);
        state.remove(-2);
        return 1;
    }

    private static void remove(Handle handle) {
        List<Handle> handles = HANDLES.get(handle.state());
        if (handles == null || !handles.remove(handle)) return;
        for (Placed placed : handle.placed()) Injections.remove(placed.method(), placed.hook());
        handle.state().unref(handle.ref());
    }

    public static void forget(LuaState state) {
        List<Handle> handles = HANDLES.remove(state.mainThread());
        if (handles == null) return;
        for (Handle handle : handles) {
            for (Placed placed : handle.placed()) Injections.remove(placed.method(), placed.hook());
        }
    }
}
