package com.meekdev.moud.addon.revo;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.script.engine.ScriptEngine;
import com.meekdev.moud.script.err.ScriptError;
import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Builtin;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Fiber;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostObject;
import com.meekdev.moud.script.host.Invocable;
import com.meekdev.moud.script.host.Members;
import com.meekdev.moud.script.host.Results;
import com.meekdev.moud.script.host.ScriptValue;
import com.meekdev.moud.script.host.Suspend;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

final class RevoEngine implements ScriptEngine {

    private static final int DEEPEST = 32;
    private static final String HANDLE = "__moud";
    private static final String VALUE = "__moud_value";
    private static final int CACHED_STRING_LENGTH = 64;
    private static final int PUSHES_PER_SWEEP = 4096;
    private static final String ANSI_COLOR = "\\[[0-9;]*m";

    private static final MethodHandle DISPATCH;

    static {
        try {
            DISPATCH = MethodHandles.lookup().findVirtual(RevoEngine.class, "dispatch",
                    MethodType.methodType(void.class, int.class, MemorySegment.class, long.class, MemorySegment.class, MemorySegment.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private interface NativeBody {
        long run(Object[] args);
    }

    private final Host host;
    private final Baton baton = new Baton("moud-revo");
    private final Arena arena = Arena.ofShared();
    private final List<NativeBody> natives = new ArrayList<>();
    private final Map<Builtin, Long> builtins = new IdentityHashMap<>();
    private final Map<Callable, Long> javaCallables = new IdentityHashMap<>();
    private final Map<Object, Integer> proxies = new HashMap<>();
    private final Map<Integer, Object> handles = new HashMap<>();
    private final Map<Long, String> atomNames = new HashMap<>();
    private final Map<String, Long> strings = new HashMap<>();
    private List<Function> fresh = new ArrayList<>();
    private MemorySegment vm;
    private long refs;
    private long proxyMeta;
    private long valueMeta;
    private long setMeta;
    private long keys;
    private long toText;
    private int nextHandle = 1;
    private long nextRef = 1;
    private int pushes;
    private boolean closed;
    private String prelude;
    private boolean reported;

    private final class Function implements Callable {
        private final long id;
        private int holds;
        private boolean dropped;

        Function(long id) {
            this.id = id;
        }

        @Override
        public Object[] call(Object... args) {
            if (dropped || closed) return new Object[0];
            return baton.onVm(() -> {
                long fn = refGet(id);
                long[] pushed = new long[args.length];
                for (int n = 0; n < args.length; n++) pushed[n] = push(args[n]);
                return new Object[] {read(invoke(fn, pushed), 0)};
            });
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
            if (!closed) baton.onVm(() -> {
                refRemove(id);
                return null;
            });
        }
    }

    private final class Ref implements ScriptValue {
        private final long id;
        private boolean dropped;

        Ref(long id) {
            this.id = id;
        }

        @Override
        public void release() {
            if (dropped || closed) return;
            dropped = true;
            baton.onVm(() -> {
                refRemove(id);
                return null;
            });
        }
    }

    RevoEngine(Host host) {
        this.host = host;
        Natives.load();
        baton.onVm(() -> {
            vm = Natives.createVm();
            if (vm.address() == 0) throw new IllegalStateException("revo could not create a vm");
            refs = table();
            setGlobal("__moud_refs", refs);
            setMeta = getGlobal("set_meta");
            toText = getGlobal("string");
            evaluate("moud", Resources.text("moud.rv"));
            keys = getGlobal("__moud_keys");
            proxyMeta = table();
            setName(proxyMeta, "__index", nativeFunction("index", args -> push(host.index(args[0], name(args[1])))));
            setName(proxyMeta, "__newindex", nativeFunction("newindex", args -> {
                host.assign(args[0], name(args[1]), args.length > 2 ? args[2] : null);
                return Natives.NIL;
            }));
            setName(proxyMeta, "__tostring", nativeFunction("tostring", args -> push(host.text(args[0]))));
            setName(proxyMeta, "__call", nativeFunction("call", args -> {
                if (!(args[0] instanceof Invocable target)) throw new HostError("attempt to call a %s", host.text(args[0]));
                Object[] rest = new Object[args.length - 1];
                System.arraycopy(args, 1, rest, 0, rest.length);
                return push(target.invoke(new Args(host, target.typeName(), rest)));
            }));
            setGlobal("__moud_proxy", proxyMeta);
            valueMeta = table();
            setName(valueMeta, "__index", nativeFunction("value", args -> push(host.index(args[0], name(args[1])))));
            setName(valueMeta, "__tostring", nativeFunction("text", args -> push(host.text(args[0]))));
            setGlobal("__moud_value_meta", valueMeta);
            for (Map.Entry<String, Object> global : host.globals().entrySet()) {
                setGlobal(global.getKey(), push(global.getValue()));
            }
            for (Map.Entry<String, Members> extension : host.extensions().entrySet()) {
                long target = getGlobal(extension.getKey());
                if (Natives.tag(target) != Natives.TABLE) {
                    target = table();
                    setGlobal(extension.getKey(), target);
                }
                Members members = extension.getValue();
                for (String name : members.names()) setName(target, name, push(members.get(name)));
            }
            return null;
        });
    }

    private long nativeFunction(String name, NativeBody body) {
        int slot = natives.size();
        natives.add(body);
        MethodHandle bound = MethodHandles.insertArguments(DISPATCH.bindTo(this), 0, slot);
        MemorySegment stub = Natives.linker().upcallStub(bound, Natives.HOST_FUNCTION, arena);
        return Natives.function(vm, name, stub);
    }

    private void dispatch(int slot, MemorySegment vmPointer, long argc, MemorySegment argv, MemorySegment out) {
        long result;
        List<Function> outer = fresh;
        fresh = new ArrayList<>();
        try {
            MemorySegment values = argv.reinterpret(argc * 8);
            Object[] args = new Object[(int) argc];
            for (int n = 0; n < argc; n++) args[n] = read(values.getAtIndex(ValueLayout.JAVA_LONG, n), 0);
            result = natives.get(slot).run(args);
        } catch (Throwable e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            try {
                baton.onCaller(() -> {
                    host.error("revo", e instanceof RuntimeException runtime ? runtime : new HostError(message));
                    return null;
                });
            } catch (Throwable ignored) {
            }
            reported = true;
            result = errorValue(message);
        } finally {
            for (Function function : fresh) {
                if (function.holds <= 0) function.drop();
            }
            fresh = outer;
        }
        out.reinterpret(8).set(ValueLayout.JAVA_LONG, 0, result);
    }

    private long errorValue(String message) {
        try {
            return Natives.error(vm, push(message));
        } catch (RuntimeException e) {
            return Natives.NIL;
        }
    }

    private long builtin(Builtin fn) {
        Long existing = builtins.get(fn);
        if (existing != null) return existing;
        long value = nativeFunction(fn.name(), args -> push(baton.onCaller(() -> host.invoke(fn, args))));
        refPut(value);
        builtins.put(fn, value);
        return value;
    }

    private long javaCallable(Callable callable) {
        Long existing = javaCallables.get(callable);
        if (existing != null) return existing;
        long value = nativeFunction("function", args -> {
            Object[] out = baton.onCaller(() -> callable.call(args));
            return push(out == null || out.length == 0 ? null : out.length == 1 ? out[0] : new Results(out));
        });
        refPut(value);
        javaCallables.put(callable, value);
        return value;
    }

    long push(Object value) {
        return switch (value) {
            case null -> Natives.NIL;
            case Boolean b -> b ? Natives.TRUE : Natives.FALSE;
            case Number n -> Natives.number(n.doubleValue());
            case String s -> string(s);
            case Builtin fn -> builtin(fn);
            case Function function -> refGet(function.id);
            case Ref ref -> refGet(ref.id);
            case Callable callable -> javaCallable(callable);
            case Results results -> list(List.of(results.values()));
            case Suspend suspend -> {
                suspend.cancel().run();
                throw new HostError("revo cannot wait, use task.delay or connect to a signal instead");
            }
            case List<?> list -> list(list);
            case Map<?, ?> map -> {
                long table = table();
                for (Map.Entry<?, ?> entry : map.entrySet()) setName(table, String.valueOf(entry.getKey()), push(entry.getValue()));
                yield table;
            }
            case Vector3 v -> value("Vector3", "x", v.x(), "y", v.y(), "z", v.z());
            case Quat q -> value("Quat", "x", q.x(), "y", q.y(), "z", q.z(), "w", q.w());
            case Color c -> value("Color", "r", c.r(), "g", c.g(), "b", c.b(), "a", c.a());
            case UDim2 u -> value("UDim2", "xScale", u.xScale(), "xOffset", u.xOffset(), "yScale", u.yScale(), "yOffset", u.yOffset());
            case CFrame c -> {
                Quat q = c.rotation();
                Vector3 p = c.position();
                yield value("CFrame", "px", p.x(), "py", p.y(), "pz", p.z(), "qx", q.x(), "qy", q.y(), "qz", q.z(), "qw", q.w());
            }
            default -> proxy(value);
        };
    }

    private long list(List<?> list) {
        long table = table();
        for (Object item : list) Natives.tablePush(vm, table, push(item));
        return table;
    }

    private long value(String type, Object... fields) {
        long table = table();
        setName(table, VALUE, string(type));
        for (int n = 0; n < fields.length; n += 2) {
            setName(table, (String) fields[n], Natives.number(((Number) fields[n + 1]).doubleValue()));
        }
        callFunction(setMeta, table, valueMeta);
        return table;
    }

    private long proxy(Object object) {
        if (++pushes % PUSHES_PER_SWEEP == 0) sweep();
        Integer known = proxies.get(object);
        if (known != null) return refGet(-known);
        int id = nextHandle++;
        long table = table();
        setName(table, HANDLE, Natives.number(id));
        callFunction(setMeta, table, proxyMeta);
        proxies.put(object, id);
        handles.put(id, object);
        refSet(-id, table);
        return table;
    }

    private void sweep() {
        for (Iterator<Map.Entry<Object, Integer>> it = proxies.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Object, Integer> entry = it.next();
            if (entry.getKey() instanceof Instance instance && !instance.isAlive()) {
                handles.remove(entry.getValue());
                refRemove(-entry.getValue());
                it.remove();
            }
        }
    }

    Object read(long data, int depth) {
        if (depth > DEEPEST) throw new HostError("table nested too deep");
        return switch (Natives.tag(data)) {
            case Natives.NUMBER -> Double.longBitsToDouble(data);
            case Natives.STRING -> text(Natives.payload(data));
            case Natives.ATOM -> {
                long id = Natives.payload(data);
                if (id <= Natives.LAST_NIL_ATOM) yield null;
                if (id == Natives.FALSE_ATOM) yield false;
                if (id == Natives.TRUE_ATOM) yield true;
                yield atomName(data);
            }
            case Natives.FUNCTION -> {
                long id = refPut(data);
                Function function = new Function(id);
                fresh.add(function);
                yield function;
            }
            case Natives.TABLE -> readTable(data, depth);
            default -> throw new HostError("that revo value cannot be used here");
        };
    }

    private Object readTable(long table, int depth) {
        long handle = getName(table, HANDLE);
        if (handle != Natives.NIL && Natives.tag(handle) == Natives.NUMBER) {
            Object object = handles.get((int) Double.longBitsToDouble(handle));
            if (object == null) throw new HostError("a destroyed instance was used");
            return object;
        }
        long kind = getName(table, VALUE);
        if (kind != Natives.NIL && Natives.tag(kind) == Natives.STRING) return valueOf(text(Natives.payload(kind)), table);
        long all = Natives.tableLength(vm, table);
        long array = Natives.arrayLength(vm, table);
        if (array > 0 && array == all) return readList(table, array, depth);
        return readMap(table, all, depth);
    }

    private List<Object> readList(long table, long length, int depth) {
        List<Object> out = new ArrayList<>((int) length);
        for (long n = 0; n < length; n++) {
            OptionalLong item = Natives.tableGetIndex(vm, table, n);
            out.add(item.isPresent() ? read(item.getAsLong(), depth + 1) : null);
        }
        return out;
    }

    private Map<String, Object> readMap(long table, long size, int depth) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (size == 0) return out;
        long names = callFunction(keys, table);
        long count = Natives.arrayLength(vm, names);
        for (long n = 0; n < count; n++) {
            OptionalLong key = Natives.tableGetIndex(vm, names, n);
            if (key.isEmpty()) continue;
            String name = keyName(key.getAsLong(), depth);
            OptionalLong found = Natives.tableGet(vm, table, key.getAsLong());
            if (found.isEmpty() || name.equals(HANDLE) || name.equals(VALUE)) continue;
            out.put(name, read(found.getAsLong(), depth + 1));
        }
        return out;
    }

    private String keyName(long key, int depth) {
        return switch (Natives.tag(key)) {
            case Natives.STRING -> text(Natives.payload(key));
            case Natives.ATOM -> atomName(key);
            default -> String.valueOf(read(key, depth + 1));
        };
    }

    private Object valueOf(String kind, long table) {
        return switch (kind) {
            case "Vector3" -> new Vector3(field(table, "x"), field(table, "y"), field(table, "z"));
            case "Quat" -> new Quat(field(table, "x"), field(table, "y"), field(table, "z"), field(table, "w"));
            case "Color" -> new Color((float) field(table, "r"), (float) field(table, "g"), (float) field(table, "b"), (float) field(table, "a"));
            case "UDim2" -> new UDim2(field(table, "xScale"), field(table, "xOffset"), field(table, "yScale"), field(table, "yOffset"));
            case "CFrame" -> new CFrame(new Vector3(field(table, "px"), field(table, "py"), field(table, "pz")),
                    new Quat(field(table, "qx"), field(table, "qy"), field(table, "qz"), field(table, "qw")));
            default -> throw new HostError("unknown value kind %s", kind);
        };
    }

    private double field(long table, String name) {
        long value = getName(table, name);
        return Natives.tag(value) == Natives.NUMBER ? Double.longBitsToDouble(value) : 0;
    }

    private String name(Object key) {
        if (key instanceof String s) return s.startsWith(":") ? s.substring(1) : s;
        if (key instanceof Double d && d == Math.rint(d)) return String.valueOf(d.longValue());
        return String.valueOf(key);
    }

    private String atomName(long atom) {
        String known = atomNames.get(atom);
        if (known != null) return known;
        long text = callFunction(toText, atom);
        String name = Natives.tag(text) == Natives.STRING ? text(Natives.payload(text)) : "";
        if (name.startsWith(":")) name = name.substring(1);
        atomNames.put(atom, name);
        return name;
    }

    private String text(long id) {
        return Natives.text(vm, id);
    }

    private long string(String s) {
        boolean cacheable = s.length() <= CACHED_STRING_LENGTH;
        Long cached = cacheable ? strings.get(s) : null;
        if (cached != null) return cached;
        long value = Natives.string(vm, s);
        if (cacheable) strings.put(s, value);
        return value;
    }

    private long table() {
        return Natives.tableCreate(vm);
    }

    private void setName(long table, String name, long value) {
        Natives.tableSetName(vm, table, name, value);
    }

    private long getName(long table, String name) {
        return Natives.tableGetName(vm, table, name).orElse(Natives.NIL);
    }

    private long getGlobal(String name) {
        return Natives.getGlobal(vm, name);
    }

    private void setGlobal(String name, long value) {
        Natives.setGlobal(vm, name, value);
    }

    private long callFunction(long fn, long... args) {
        return invoke(fn, args);
    }

    private long invoke(long fn, long[] args) {
        reported = false;
        OptionalLong result = Natives.call(vm, fn, args);
        if (result.isPresent()) return result.getAsLong();
        if (reported) return Natives.NIL;
        throw new ScriptError("revo", lastError(), null);
    }

    private String lastError() {
        String text = Natives.lastError(vm);
        if (text.isEmpty()) return "a revo call failed";
        return text.replaceAll(ANSI_COLOR, "");
    }

    private long refPut(long value) {
        long id = nextRef++;
        refSet(id, value);
        return id;
    }

    private void refSet(long id, long value) {
        Natives.tableSet(vm, refs, Natives.number(id), value);
    }

    private long refGet(long id) {
        return Natives.tableGet(vm, refs, Natives.number(id)).orElse(Natives.NIL);
    }

    private void refRemove(long id) {
        Natives.tableRemove(vm, refs, Natives.number(id));
    }

    private long evaluate(String chunk, String source) {
        OptionalLong result = Natives.eval(vm, chunk, source);
        if (result.isEmpty()) throw new ScriptError(chunk, lastError(), null);
        return result.getAsLong();
    }

    private String typed(String source) {
        if (prelude == null) prelude = RevoTypes.prelude(host.api(), host.classes());
        return prelude + source;
    }

    @Override
    public void run(String chunk, String source) {
        baton.onVm(() -> evaluate(chunk, typed(source)));
    }

    @Override
    public Object module(String chunk, String source) {
        return baton.onVm(() -> {
            long eval = getName(getGlobal("revo"), "eval");
            if (Natives.tag(eval) != Natives.FUNCTION) throw new HostError("this revo has no revo.eval to load modules with");
            long value = unwrap(chunk, invoke(eval, new long[] {string(source)}));
            int tag = Natives.tag(value);
            if (tag == Natives.TABLE || tag == Natives.FUNCTION) return new Ref(refPut(value));
            return read(value, 0);
        });
    }

    private long unwrap(String chunk, long result) {
        if (Natives.isOk(vm, result)) return Natives.okValue(vm, result);
        if (Natives.isError(vm, result)) throw new HostError("%s: %s", chunk, host.text(read(result, 0)));
        return result;
    }

    @Override
    public Fiber fiber(Callable fn) {
        return null;
    }

    @Override
    public Fiber script(String chunk, String source, Instance script) {
        baton.onVm(() -> {
            setGlobal("script", push(script));
            return evaluate(chunk, typed(source));
        });
        return null;
    }

    @Override
    public ScriptValue table(Map<String, Object> data) {
        return baton.onVm(() -> new Ref(refPut(push(data))));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> read(ScriptValue table) {
        if (!(table instanceof Ref ref) || ref.dropped) return Map.of();
        return baton.onVm(() -> read(refGet(ref.id), 0) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            baton.onVm(() -> {
                Natives.destroyVm(vm);
                return null;
            });
        } catch (Throwable ignored) {
        }
        baton.close();
    }
}
