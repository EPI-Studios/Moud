package com.meekdev.moud.script.mixin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Dispatch {

    public enum Kind { ARGS, BEFORE, REPLACE, AFTER, REDIRECT, CONSTANT, VARIABLE, FIELD }

    public static final Object PROCEED = new Object();

    public abstract static class Hook {
        public final Kind kind;
        public final int priority;
        final Thread owner;
        private long calls;
        private long nanos;
        private long errors;

        protected Hook(Kind kind, int priority, Thread owner) {
            this.kind = kind;
            this.priority = priority;
            this.owner = owner;
        }

        protected abstract boolean ready();

        protected abstract Object run(Call call) throws Throwable;

        protected void failed(Throwable error) {}

        public long calls() {
            return calls;
        }

        public long nanos() {
            return nanos;
        }

        public long errors() {
            return errors;
        }

        final boolean mine(Thread thread) {
            return owner == thread && ready();
        }

        final Object fire(Call call) {
            long started = System.nanoTime();
            calls++;
            try {
                return run(call);
            } catch (Proceeding e) {
                throw e;
            } catch (Throwable e) {
                errors++;
                failed(e);
                return PROCEED;
            } finally {
                nanos += System.nanoTime() - started;
            }
        }
    }

    public static final class Call {
        public final Object self;
        public Object[] args;
        public final Class<?>[] parameters;
        public final Class<?> returns;
        public Object value;
        private final List<Hook> chain;
        private final MethodHandle original;
        private final boolean receiver;
        private final String bypass;
        private int next;

        Call(Object self, Object[] args, Class<?>[] parameters, Class<?> returns, Object value, List<Hook> chain,
             MethodHandle original, boolean receiver, String bypass) {
            this.self = self;
            this.args = args;
            this.parameters = parameters;
            this.returns = returns;
            this.value = value;
            this.chain = chain;
            this.original = original;
            this.receiver = receiver;
            this.bypass = bypass;
        }

        public Object proceed(Object self, Object[] args) {
            if (next < chain.size()) {
                Hook hook = chain.get(next++);
                Call inner = new Call(self, args, parameters, returns, null, chain, original, receiver, bypass);
                inner.next = next;
                Object out = hook.fire(inner);
                if (out != PROCEED) return out;
                return inner.proceed(self, args);
            }
            Frame frame = FRAME.get();
            int inside = frame.inside;
            frame.inside = 0;
            if (bypass != null) frame.bypass = bypass;
            try {
                return original.invokeWithArguments(receiver ? prepend(self, args) : args);
            } catch (Throwable e) {
                throw new Proceeding(e);
            } finally {
                frame.bypass = null;
                frame.inside = inside;
            }
        }
    }

    public static final class Proceeding extends RuntimeException {
        Proceeding(Throwable cause) {
            super(cause.getMessage(), cause, false, false);
        }
    }

    public static final class Cancelled {
        public final Object value;

        Cancelled(Object value) {
            this.value = value;
        }
    }

    static final class Target {
        final Method method;
        final MethodHandle original;
        final boolean statics;
        final List<Hook> hooks = new CopyOnWriteArrayList<>();

        Target(Method method, MethodHandle original, boolean statics) {
            this.method = method;
            this.original = original;
            this.statics = statics;
        }
    }

    static final class Site {
        final String id;
        final Class<?>[] parameters;
        final Class<?> returns;
        final MethodHandle original;
        final boolean receiver;
        final List<Hook> hooks = new CopyOnWriteArrayList<>();

        Site(String id, Class<?>[] parameters, Class<?> returns, MethodHandle original, boolean receiver) {
            this.id = id;
            this.parameters = parameters;
            this.returns = returns;
            this.original = original;
            this.receiver = receiver;
        }
    }

    static final class Watch {
        final String id;
        final VarHandle handle;
        final boolean statics;
        final Class<?> type;
        final List<Hook> hooks = new CopyOnWriteArrayList<>();

        Watch(String id, VarHandle handle, boolean statics, Class<?> type) {
            this.id = id;
            this.handle = handle;
            this.statics = statics;
            this.type = type;
        }

        Object read(Object self) {
            return statics ? handle.get() : handle.get(self);
        }

        void write(Object self, Object value) {
            if (statics) handle.set(value);
            else handle.set(self, value);
        }
    }

    private static final class Frame {
        String bypass;
        int inside;
    }

    public static final Object PASS = new Object();
    private static final Object[] NONE = new Object[0];
    private static final Class<?>[] NONE_TYPES = new Class<?>[0];
    private static final Comparator<Hook> ORDER = Comparator.comparingInt((Hook hook) -> -hook.priority);

    static final Map<String, Target> TARGETS = new ConcurrentHashMap<>();
    static final Map<String, Site> SITES = new ConcurrentHashMap<>();
    static final Map<String, Watch> WATCHES = new ConcurrentHashMap<>();
    private static final ThreadLocal<Frame> FRAME = ThreadLocal.withInitial(Frame::new);

    private Dispatch() {}

    static void add(List<Hook> hooks, Hook hook) {
        List<Hook> sorted = new ArrayList<>(hooks);
        sorted.add(hook);
        sorted.sort(ORDER);
        hooks.clear();
        hooks.addAll(sorted);
    }

    public static Object head(String id, Object self, Object[] args) {
        Frame frame = FRAME.get();
        if (frame.bypass != null && frame.bypass.equals(id)) {
            frame.bypass = null;
            return PASS;
        }
        Target target = TARGETS.get(id);
        if (target == null || target.hooks.isEmpty() || frame.inside > 0) return null;
        Thread thread = Thread.currentThread();
        List<Hook> replaces = null;
        Call call = null;
        frame.inside++;
        try {
            for (Hook hook : target.hooks) {
                if (hook.kind == Kind.AFTER || !hook.mine(thread)) continue;
                if (call == null) call = new Call(self, args, target.method.getParameterTypes(), target.method.getReturnType(), null, List.of(), null, false, null);
                switch (hook.kind) {
                    case ARGS -> hook.fire(call);
                    case BEFORE -> {
                        Object out = hook.fire(call);
                        if (out != PROCEED) return new Cancelled(orDefault(out, call.returns));
                    }
                    case REPLACE -> {
                        if (replaces == null) replaces = new ArrayList<>();
                        replaces.add(hook);
                    }
                    default -> {}
                }
            }
            if (call != null && call.args != args) System.arraycopy(call.args, 0, args, 0, Math.min(args.length, call.args.length));
            if (replaces == null) return null;
            Call around = new Call(self, args, call.parameters, call.returns, null, replaces, target.original, !target.statics, id);
            Object value = around.proceed(self, args);
            return new Cancelled(orDefault(after(target, frame, self, args, value, thread), call.returns));
        } catch (Proceeding e) {
            throw sneaky(e.getCause());
        } finally {
            frame.inside--;
        }
    }

    public static Object tail(String id, Object self, Object[] args, Object result) {
        Target target = TARGETS.get(id);
        Frame frame = FRAME.get();
        if (target == null || target.hooks.isEmpty() || frame.inside > 0) return result;
        frame.inside++;
        try {
            return orDefault(after(target, frame, self, args, result, Thread.currentThread()), target.method.getReturnType());
        } finally {
            frame.inside--;
        }
    }

    private static Object after(Target target, Frame frame, Object self, Object[] args, Object result, Thread thread) {
        Call call = null;
        for (Hook hook : target.hooks) {
            if (hook.kind != Kind.AFTER || !hook.mine(thread)) continue;
            if (call == null) call = new Call(self, args, target.method.getParameterTypes(), target.method.getReturnType(), result, List.of(), null, false, null);
            Object out = hook.fire(call);
            if (out != PROCEED) call.value = out;
        }
        return call == null ? result : call.value;
    }

    public static Object cancelled(Object entered) {
        return ((Cancelled) entered).value;
    }

    public static Object redirect(Object receiver, Object[] args, String id) throws Throwable {
        Site site = SITES.get(id);
        Frame frame = FRAME.get();
        List<Hook> chain = List.of();
        if (frame.inside == 0 && !site.hooks.isEmpty()) {
            frame.inside++;
            try {
                chain = ready(site.hooks);
            } finally {
                frame.inside--;
            }
        }
        if (chain.isEmpty()) {
            return site.original.invokeWithArguments(site.receiver ? prepend(receiver, args) : args);
        }
        frame.inside++;
        try {
            Call call = new Call(site.receiver ? receiver : null, args, site.parameters, site.returns, null, chain, site.original, site.receiver, null);
            return orDefault(call.proceed(call.self, args), site.returns);
        } catch (Proceeding e) {
            throw e.getCause();
        } finally {
            frame.inside--;
        }
    }

    public static void putObject(Object self, Object value, String id) {
        put(self, value, id);
    }

    public static void putInt(Object self, int value, String id) {
        put(self, value, id);
    }

    public static void putLong(Object self, long value, String id) {
        put(self, value, id);
    }

    public static void putFloat(Object self, float value, String id) {
        put(self, value, id);
    }

    public static void putDouble(Object self, double value, String id) {
        put(self, value, id);
    }

    private static void put(Object self, Object value, String id) {
        Watch watch = WATCHES.get(id);
        Object written = narrow(value, watch.type);
        Frame frame = FRAME.get();
        if (frame.inside == 0 && !watch.hooks.isEmpty()) {
            Thread thread = Thread.currentThread();
            Object old = null;
            boolean read = false;
            frame.inside++;
            try {
                for (Hook hook : watch.hooks) {
                    if (!hook.mine(thread)) continue;
                    if (!read) {
                        old = watch.read(self);
                        read = true;
                    }
                    if (Objects.equals(old, written)) break;
                    Object out = hook.fire(new Call(self, new Object[] {old}, NONE_TYPES, watch.type, written, List.of(), null, false, null));
                    if (out != PROCEED && out != null) written = narrow(out, watch.type);
                }
            } finally {
                frame.inside--;
            }
        }
        watch.write(self, written);
    }

    private static Object narrow(Object value, Class<?> type) {
        if (!type.isPrimitive() || !(value instanceof Number n)) return value;
        if (type == int.class) return n.intValue();
        if (type == long.class) return n.longValue();
        if (type == float.class) return n.floatValue();
        if (type == double.class) return n.doubleValue();
        if (type == short.class) return n.shortValue();
        if (type == byte.class) return n.byteValue();
        if (type == boolean.class) return n.intValue() != 0;
        if (type == char.class) return (char) n.intValue();
        return value;
    }

    public static Object constant(Object value, String id) {
        return transform(value, id);
    }

    public static Object variable(Object value, String id) {
        return transform(value, id);
    }

    private static Object transform(Object value, String id) {
        Site site = SITES.get(id);
        Frame frame = FRAME.get();
        if (site == null || frame.inside > 0) return value;
        Thread thread = Thread.currentThread();
        Object current = value;
        frame.inside++;
        try {
            for (Hook hook : site.hooks) {
                if (!hook.mine(thread)) continue;
                Object out = hook.fire(new Call(null, NONE, site.parameters, site.returns, current, List.of(), null, false, null));
                if (out != PROCEED) current = out;
            }
        } finally {
            frame.inside--;
        }
        return current == null ? value : current;
    }

    private static List<Hook> ready(List<Hook> hooks) {
        Thread thread = Thread.currentThread();
        List<Hook> out = null;
        for (Hook hook : hooks) {
            if (!hook.mine(thread)) continue;
            if (out == null) out = new ArrayList<>(2);
            out.add(hook);
        }
        return out == null ? List.of() : out;
    }

    static Object[] prepend(Object first, Object[] rest) {
        Object[] out = new Object[rest.length + 1];
        out[0] = first;
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }

    static Object orDefault(Object value, Class<?> type) {
        if (value != null || !type.isPrimitive()) return value;
        if (type == boolean.class) return false;
        if (type == char.class) return (char) 0;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneaky(Throwable error) throws E {
        throw (E) error;
    }
}
