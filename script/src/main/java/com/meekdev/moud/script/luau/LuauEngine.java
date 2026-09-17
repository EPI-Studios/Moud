package com.meekdev.moud.script.luau;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.engine.ScriptEngine;
import com.meekdev.moud.script.err.ScriptError;
import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Builtin;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Fiber;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.Invocable;
import com.meekdev.moud.script.host.Members;
import com.meekdev.moud.script.host.Results;
import com.meekdev.moud.script.host.ScriptValue;
import com.meekdev.moud.script.host.Values;
import com.meekdev.moud.script.host.Suspend;
import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.BuilinLibrary;
import net.hollowcube.luau.LuaError;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaStatus;
import net.hollowcube.luau.LuaType;
import net.hollowcube.luau.compiler.LuauCompileException;
import net.hollowcube.luau.compiler.LuauCompiler;

final class LuauEngine implements ScriptEngine {

    private static final int HOST = 1;
    private static final int DEEPEST = 32;
    private static final String SCRIPT_LOCAL = LuauResource.text("script-local.luau").strip();

    private static final BuilinLibrary[] LIBRARIES = {
        BuilinLibrary.BASE, BuilinLibrary.COROUTINE, BuilinLibrary.TABLE, BuilinLibrary.STRING, BuilinLibrary.MATH,
        BuilinLibrary.BIT32, BuilinLibrary.BUFFER, BuilinLibrary.UTF8, BuilinLibrary.VECTOR,
    };

    private final Host host;
    private final LuaState state;
    private final Map<Builtin, Integer> builtins = new IdentityHashMap<>();
    private Suspend pending;
    private int resumedWith;
    private List<Function> fresh = new ArrayList<>();
    private boolean closed;

    private final class Function implements Callable {
        private final int ref;
        private int holds;
        private boolean dropped;

        Function(int ref) {
            this.ref = ref;
        }

        @Override
        public Object[] call(Object... args) {
            if (dropped || closed) return new Object[0];
            int base = state.top();
            state.getRef(ref);
            for (Object arg : args) push(state, arg);
            try {
                state.call(args.length, -1);
                int count = state.top() - base;
                Object[] out = new Object[count];
                for (int n = 0; n < count; n++) out[n] = read(state, base + 1 + n, 0);
                return out;
            } finally {
                state.top(base);
            }
        }

        @Override
        public Callable retain() {
            holds++;
            return this;
        }

        @Override
        public void release() {
            if (--holds <= 0) drop();
        }

        void drop() {
            if (dropped) return;
            dropped = true;
            if (!closed) state.unref(ref);
        }
    }

    private final class Ref implements ScriptValue {
        private final int ref;
        private boolean dropped;

        Ref(int ref) {
            this.ref = ref;
        }

        @Override
        public void release() {
            if (dropped) return;
            dropped = true;
            if (!closed) state.unref(ref);
        }
    }

    private final class LuauFiber implements Fiber {
        private final LuaState thread;
        private final int threadRef;
        private Object[] initial;
        private Object[] results = new Object[0];
        private boolean finished;

        LuauFiber(LuaState thread, int threadRef, Object[] initial) {
            this.thread = thread;
            this.threadRef = threadRef;
            this.initial = initial;
        }

        @Override
        public Suspend resume(Object... values) {
            if (finished) return null;
            Object[] args = values;
            if (initial != null) {
                args = initial;
                initial = null;
            }
            for (Object arg : args) push(thread, arg);
            resumedWith = args.length;
            LuaStatus status;
            try {
                status = thread.resume(state, args.length);
            } catch (RuntimeException e) {
                finish();
                throw e;
            }
            if (status == LuaStatus.YIELD) {
                Suspend next = pending;
                pending = null;
                if (next == null) next = Suspend.seconds(thread.top() > 0 && thread.type(-1) == LuaType.NUMBER ? thread.toNumber(-1) : 0);
                thread.pop(thread.top());
                return next;
            }
            String failure = status == LuaStatus.OK ? null : thread.toString(-1);
            if (failure == null) {
                results = new Object[thread.top()];
                for (int n = 0; n < results.length; n++) results[n] = read(thread, n + 1, 0);
            }
            finish();
            if (failure != null) throw new ScriptError("task", failure, null);
            return null;
        }

        @Override
        public Object[] results() {
            return results;
        }

        @Override
        public void cancel() {
            finish();
        }

        private void finish() {
            if (finished) return;
            finished = true;
            if (!closed) state.unref(threadRef);
        }
    }

    LuauEngine(Host host) {
        this.host = host;
        this.state = LuaState.newState();
        state.openLibs(LIBRARIES);
        installMetatable();
        installErrors();
        for (Map.Entry<String, Object> global : host.globals().entrySet()) {
            push(state, global.getValue());
            state.setGlobal(global.getKey());
        }
        for (Map.Entry<String, Members> extension : host.extensions().entrySet()) {
            state.getGlobal(extension.getKey());
            if (state.type(-1) != LuaType.TABLE) {
                state.pop(1);
                state.newTable();
                state.pushValue(-1);
                state.setGlobal(extension.getKey());
            }
            Members members = extension.getValue();
            for (String name : members.names()) {
                push(state, members.get(name));
                state.rawSetField(-2, name);
            }
            state.pop(1);
        }
    }

    private void installErrors() {
        state.load("errors", compile("errors", LuauResource.text("errors.luau")));
        state.pushFunction(LuaFunc.wrap(s -> {
            if (s.type(1) == LuaType.USERDATA && s.toUserData(1) instanceof LuaError error) {
                s.pushString(String.valueOf(error.getMessage()));
            } else {
                s.pushValue(1);
            }
            return 1;
        }, "errors.message"));
        state.call(1, 0);
    }

    private void installMetatable() {
        state.newTable();
        metamethod("__index", s -> {
            String key = s.type(2) == LuaType.STRING ? s.toString(2) : s.toStringRepr(2);
            return one(s, host.index(s.toUserDataTagged(1, HOST), key));
        });
        metamethod("__newindex", s -> {
            host.assign(s.toUserDataTagged(1, HOST), s.checkString(2), read(s, 3, 0));
            return 0;
        });
        metamethod("__tostring", s -> {
            s.pushString(host.text(s.toUserDataTagged(1, HOST)));
            return 1;
        });
        metamethod("__eq", s -> {
            s.pushBoolean(host.equal(read(s, 1, 0), read(s, 2, 0)));
            return 1;
        });
        metamethod("__call", s -> {
            if (!(s.toUserDataTagged(1, HOST) instanceof Invocable target)) throw new HostError("attempt to call a %s", host.text(s.toUserDataTagged(1, HOST)));
            Object[] args = arguments(s, 2);
            return results(s, target.invoke(new Args(host, target.typeName(), args)));
        });
        metamethod("__add", s -> one(s, host.operate(Host.Op.ADD, read(s, 1, 0), read(s, 2, 0))));
        metamethod("__sub", s -> one(s, host.operate(Host.Op.SUB, read(s, 1, 0), read(s, 2, 0))));
        metamethod("__mul", s -> one(s, host.operate(Host.Op.MUL, read(s, 1, 0), read(s, 2, 0))));
        metamethod("__div", s -> one(s, host.operate(Host.Op.DIV, read(s, 1, 0), read(s, 2, 0))));
        metamethod("__unm", s -> one(s, host.operate(Host.Op.UNM, read(s, 1, 0), null)));
        state.setUserDataMetaTable(HOST);
    }

    private void metamethod(String name, ToIntFunction<LuaState> body) {
        state.pushFunction(LuaFunc.wrap(s -> guarded(s, body), "host." + name));
        state.rawSetField(-2, name);
    }

    private int guarded(LuaState s, ToIntFunction<LuaState> body) {
        List<Function> outer = fresh;
        fresh = new ArrayList<>();
        try {
            return body.applyAsInt(s);
        } catch (LuaError e) {
            throw e;
        } catch (HostError | ScriptError e) {
            throw s.error("%s", e.getMessage());
        } catch (RuntimeException e) {
            throw s.error("%s", String.valueOf(e.getMessage() == null ? e : e.getMessage()));
        } finally {
            for (Function function : fresh) {
                if (function.holds <= 0) function.drop();
            }
            fresh = outer;
        }
    }

    private int one(LuaState s, Object value) {
        push(s, value);
        return 1;
    }

    private Object[] arguments(LuaState s, int first) {
        int top = s.top();
        Object[] args = new Object[Math.max(0, top - first + 1)];
        for (int at = first; at <= top; at++) args[at - first] = read(s, at, 0);
        return args;
    }

    private int results(LuaState s, Object result) {
        switch (result) {
            case null -> {
                return 0;
            }
            case Results many -> {
                for (Object value : many.values()) push(s, value);
                return many.values().length;
            }
            case Suspend suspend -> {
                if (!s.isYieldable()) {
                    suspend.cancel().run();
                    throw new HostError("cannot wait here, run this inside task.spawn or a script");
                }
                pending = suspend;
                return s.yield(0);
            }
            default -> {
                push(s, result);
                return 1;
            }
        }
    }

    private int resumed(LuaState s, LuaStatus status) {
        int count = resumedWith;
        if (count == 1 && s.type(-1) == LuaType.USERDATA && s.toUserDataTagged(-1, HOST) instanceof Suspend.Failure failure) {
            throw s.error("%s", failure.message());
        }
        return count;
    }

    private void pushBuiltin(LuaState s, Builtin fn) {
        Integer ref = builtins.get(fn);
        if (ref == null) {
            state.pushFunction(LuaFunc.yieldable(t -> guarded(t, u -> results(u, host.invoke(fn, arguments(u, 1)))), this::resumed, fn.name(), Arena.ofShared()));
            ref = state.ref(-1);
            state.pop(1);
            builtins.put(fn, ref);
        }
        s.getRef(ref);
    }

    void push(LuaState s, Object value) {
        switch (value) {
            case null -> s.pushNil();
            case Boolean b -> s.pushBoolean(b);
            case Number n -> s.pushNumber(n.doubleValue());
            case String text -> s.pushString(text);
            case Builtin fn -> pushBuiltin(s, fn);
            case Function function -> s.getRef(function.ref);
            case Ref ref -> s.getRef(ref.ref);
            case Callable callable -> s.pushFunction(LuaFunc.wrap(t -> guarded(t, u -> {
                Object[] out = callable.call(arguments(u, 1));
                return results(u, out == null ? Results.NONE : new Results(out));
            }), "function"));
            case List<?> list -> {
                s.createTable(list.size(), 0);
                for (int n = 0; n < list.size(); n++) {
                    push(s, list.get(n));
                    s.rawSetI(-2, n + 1);
                }
            }
            case Map<?, ?> map -> {
                s.createTable(0, map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    push(s, entry.getValue());
                    s.rawSetField(-2, String.valueOf(entry.getKey()));
                }
            }
            default -> s.newUserDataTaggedWithMetatable(value, HOST);
        }
    }

    Object read(LuaState s, int at, int depth) {
        if (depth > DEEPEST) throw new HostError("table nested too deep");
        int index = s.absIndex(at);
        LuaType type = s.type(index);
        return switch (type) {
            case NIL, NONE -> null;
            case BOOLEAN -> s.toBoolean(index);
            case NUMBER, INTEGER -> s.toNumber(index);
            case STRING -> s.toString(index);
            case USERDATA -> s.toUserDataTagged(index, HOST);
            case FUNCTION -> {
                s.pushValue(index);
                Function function = new Function(s.ref(-1));
                s.pop(1);
                fresh.add(function);
                yield function;
            }
            case TABLE -> table(s, index, depth);
            default -> throw new HostError("a %s cannot be used here", type.typeName());
        };
    }

    private Object table(LuaState s, int at, int depth) {
        int length = s.len(at);
        if (length > 0 && !keyed(s, at, length)) {
            List<Object> list = new ArrayList<>(length);
            for (int n = 1; n <= length; n++) {
                s.rawGetI(at, n);
                list.add(read(s, -1, depth + 1));
                s.pop(1);
            }
            return list;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        s.pushNil();
        while (s.next(at)) {
            String key = switch (s.type(-2)) {
                case STRING -> s.toString(-2);
                case NUMBER, INTEGER -> s.toStringRepr(-2);
                default -> throw new HostError("tables must be lists or keyed by strings");
            };
            map.put(key, read(s, -1, depth + 1));
            s.pop(1);
        }
        return map;
    }

    private static boolean keyed(LuaState s, int at, int length) {
        int count = 0;
        s.pushNil();
        while (s.next(at)) {
            s.pop(1);
            if (++count > length) {
                s.pop(1);
                return true;
            }
        }
        return false;
    }

    private static String withScript(String source) {
        return SCRIPT_LOCAL + " " + source;
    }

    private byte[] compile(String chunk, String source) {
        try {
            return LuauCompiler.DEFAULT.compile(source);
        } catch (LuauCompileException e) {
            throw new ScriptError(chunk, e.getMessage(), e);
        }
    }

    @Override
    public void run(String chunk, String source) {
        byte[] bytecode = compile(chunk, source);
        state.load(chunk, bytecode);
        state.call(0, 0);
    }

    @Override
    public Object module(String chunk, String source) {
        return module(chunk, source, null);
    }

    @Override
    public Object module(String chunk, String source, Instance script) {
        byte[] bytecode;
        try {
            bytecode = LuauCompiler.DEFAULT.compile(script == null ? source : withScript(source));
        } catch (LuauCompileException e) {
            throw new HostError("res://%s does not compile: %s", chunk, e.getMessage());
        }
        int before = state.top();
        try {
            state.load(chunk, bytecode);
            if (script != null) push(state, script);
            state.call(script == null ? 0 : 1, -1);
        } catch (LuaError e) {
            state.top(before);
            String why = e.getMessage();
            if (why != null && (why.contains("attempt to yield across") || why.contains("cannot wait here"))) {
                throw new HostError("res://%s yielded while loading, modules cannot yield at load time", chunk);
            }
            throw new HostError("res://%s: %s", chunk, why);
        }
        int returned = state.top() - before;
        if (returned != 1) {
            state.top(before);
            throw new HostError("res://%s must return exactly one value, returned %d", chunk, returned);
        }
        LuaType type = state.type(-1);
        if (type == LuaType.TABLE || type == LuaType.FUNCTION || type == LuaType.USERDATA) {
            return new Ref(state.ref(-1));
        }
        Object value = read(state, -1, 0);
        state.top(before);
        return value;
    }

    @Override
    public Fiber fiber(Callable fn) {
        if (!(fn instanceof Function function)) return null;
        LuaState thread = state.newThread();
        int threadRef = state.ref(-1);
        state.pop(1);
        state.getRef(function.ref);
        state.xmove(thread, 1);
        return new LuauFiber(thread, threadRef, null);
    }

    @Override
    public Fiber script(String chunk, String source, Instance script) {
        byte[] bytecode = compile(chunk, withScript(source));
        LuaState thread = state.newThread();
        int threadRef = state.ref(-1);
        state.pop(1);
        thread.load(chunk, bytecode);
        return new LuauFiber(thread, threadRef, new Object[] {script});
    }

    @Override
    public ScriptValue table(Map<String, Object> data) {
        push(state, data);
        return new Ref(state.ref(-1));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> read(ScriptValue table) {
        if (!(table instanceof Ref ref) || ref.dropped) return Map.of();
        int before = state.top();
        try {
            state.getRef(ref.ref);
            Object value = state.type(-1) == LuaType.TABLE ? plain(state, state.top(), 0) : null;
            return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        } finally {
            state.top(before);
        }
    }

    private Object plain(LuaState s, int at, int depth) {
        if (depth >= 16) return null;
        return switch (s.type(at)) {
            case NUMBER, INTEGER -> s.toNumber(at);
            case BOOLEAN -> s.toBoolean(at);
            case STRING -> s.toString(at);
            case USERDATA -> Values.isValue(s.toUserDataTagged(at, HOST)) ? s.toUserDataTagged(at, HOST) : null;
            case TABLE -> {
                Map<String, Object> out = new LinkedHashMap<>();
                s.pushNil();
                while (s.next(at)) {
                    LuaType keyType = s.type(-2);
                    if (keyType == LuaType.STRING || keyType == LuaType.NUMBER) {
                        Object value = plain(s, s.top(), depth + 1);
                        if (value != null) out.put(s.toStringRepr(-2), value);
                    }
                    s.pop(1);
                }
                yield out;
            }
            default -> null;
        };
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        pending = null;
        state.close();
    }
}
