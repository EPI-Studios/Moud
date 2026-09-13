package com.meekdev.moud.script.bind;

import com.meekdev.moud.script.err.ScriptError;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import com.meekdev.moud.script.sched.Ownership;
import java.util.List;
import java.util.function.Consumer;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

public final class Signals {

    static final int TAG = 6;
    static final int CONNECTION = 7;

    private static final String METHODS = "moud.signal.methods";
    private static final String CONNECTION_METHODS = "moud.connection.methods";

    private Signals() {}

    public static void install(LuaState state) {
        state.newTable();
        state.pushFunction(LuaFunc.wrap(Signals::connect, "Signal:connect"));
        state.rawSetField(-2, "connect");
        state.rawSetField(LuaState.REGISTRY_INDEX, METHODS);

        // a luau file adds the signal methods that wait or count
        state.pushFunction(LuaFunc.wrap(s -> {
            String name = s.checkString(1);
            s.rawGetField(LuaState.REGISTRY_INDEX, METHODS);
            s.pushValue(2);
            s.rawSetField(-2, name);
            s.pop(1);
            return 0;
        }, "__moud_signal_method"));
        state.setGlobal("__moud_signal_method");

        // seconds on a steady clock, for measuring how long something took
        state.pushFunction(LuaFunc.wrap(s -> {
            s.pushNumber(System.nanoTime() / 1e9);
            return 1;
        }, "clock"));
        state.setGlobal("clock");

        state.newTable();
        state.pushFunction(LuaFunc.wrap(Signals::disconnect, "Connection:disconnect"));
        state.rawSetField(-2, "disconnect");
        state.rawSetField(LuaState.REGISTRY_INDEX, CONNECTION_METHODS);

        state.newTable();
        state.pushFunction(LuaFunc.wrap(s -> members(s, METHODS, "signal"), "Signal.__index"));
        state.rawSetField(-2, "__index");
        state.setUserDataMetaTable(TAG);

        state.newTable();
        state.pushFunction(LuaFunc.wrap(s -> members(s, CONNECTION_METHODS, "connection"), "Connection.__index"));
        state.rawSetField(-2, "__index");
        state.setUserDataMetaTable(CONNECTION);
    }

    public static void push(LuaState state, Handlers signal) {
        state.newUserDataTaggedWithMetatable(signal, TAG);
    }

    // one handler failing does not stop the rest, which is what 8.8 asks for
    public static void fire(LuaState state, Handlers signal, Consumer<ScriptError> onError, Args args) {
        Ownership owners = Ownership.of(state);
        for (int ref : signal.snapshot()) {
            if (state.getRef(ref) != LuaType.FUNCTION) {
                state.pop(1);
                continue;
            }
            int pushed = args.push(state);
            // a handler runs as the script that connected it, so what it connects belongs to that script too
            Object owner = signal.owner(ref);
            Object before = owners.enter(owner);
            long started = System.nanoTime();
            try {
                state.call(pushed, 0);
            } catch (RuntimeException e) {
                onError.accept(new ScriptError("signal", e.getMessage(), e));
            } finally {
                owners.leave(before);
                Profiler.add(state, Profiler.owner(owner), System.nanoTime() - started);
            }
        }
    }

    private static int members(LuaState state, String methods, String what) {
        String key = state.checkString(2);
        state.rawGetField(LuaState.REGISTRY_INDEX, methods);
        if (state.rawGetField(-1, key) != LuaType.NIL) {
            state.remove(-2);
            return 1;
        }
        throw state.error("%s has no member '%s'", what, key);
    }

    private static int connect(LuaState state) {
        Handlers signal = (Handlers) state.toUserDataTagged(1, TAG);
        if (signal == null) throw state.error("not a signal");
        if (!state.isFunction(2)) throw state.error("connect wants a function");
        state.pushValue(2);
        int ref = state.ref(-1);
        state.pop(1);
        Ownership owners = Ownership.of(state);
        Object owner = owners.current();
        signal.add(ref, owner);
        owners.onRelease(owner, () -> {
            if (signal.remove(ref)) state.unref(ref);
        });
        state.newUserDataTaggedWithMetatable(new Connection(signal, ref), CONNECTION);
        return 1;
    }

    private static int disconnect(LuaState state) {
        Connection connection = (Connection) state.toUserDataTagged(1, CONNECTION);
        if (connection == null) throw state.error("not a connection");
        if (connection.signal.remove(connection.ref)) state.unref(connection.ref);
        return 0;
    }

    @FunctionalInterface
    public interface Args {
        int push(LuaState state);
    }

    // copy on write, so connecting or disconnecting from inside a handler is safe
    public static final class Handlers {
        private List<Integer> refs = List.of();
        private final Map<Integer, Object> owners = new HashMap<>();

        void add(int ref, Object owner) {
            List<Integer> next = new ArrayList<>(refs);
            next.add(ref);
            refs = next;
            if (owner != null) owners.put(ref, owner);
        }

        boolean remove(int ref) {
            if (!refs.contains(ref)) return false;
            List<Integer> next = new ArrayList<>(refs);
            next.remove(Integer.valueOf(ref));
            refs = next;
            owners.remove(ref);
            return true;
        }

        Object owner(int ref) {
            return owners.get(ref);
        }

        List<Integer> snapshot() {
            return refs;
        }

        public int count() {
            return refs.size();
        }
    }

    private record Connection(Handlers signal, int ref) {}
}
