package com.meekdev.moud.script.mixin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Dispatch {

    public interface Hook {
        Thread owner();

        void run(Call call);
    }

    public static final class Call {
        public final Object self;
        public final Object[] args;
        public final Class<?>[] parameters;
        public final Class<?> returns;
        public final boolean returning;
        boolean cancelled;
        Object value;

        Call(Object self, Object[] args, Target target, boolean returning, Object value) {
            this.self = self;
            this.args = args;
            this.parameters = target.parameters;
            this.returns = target.returns;
            this.returning = returning;
            this.value = value;
        }

        public void cancel(Object result) {
            if (returning) throw new IllegalStateException("the method already ran, set its return value instead");
            cancelled = true;
            value = result;
        }

        public boolean cancelled() {
            return cancelled;
        }

        public Object value() {
            return value;
        }

        public void value(Object result) {
            if (!returning) {
                cancelled = true;
            }
            value = result;
        }
    }

    static final class Target {
        final Class<?>[] parameters;
        final Class<?> returns;
        final List<Hook> heads = new CopyOnWriteArrayList<>();
        final List<Hook> tails = new CopyOnWriteArrayList<>();

        Target(Class<?>[] parameters, Class<?> returns) {
            this.parameters = parameters;
            this.returns = returns;
        }
    }

    public static final class Cancelled {
        final Object value;

        Cancelled(Object value) {
            this.value = value;
        }
    }

    static final Map<String, Target> TARGETS = new ConcurrentHashMap<>();

    private static final ThreadLocal<int[]> INSIDE = ThreadLocal.withInitial(() -> new int[1]);

    private Dispatch() {}

    public static Object head(String id, Object self, Object[] args) {
        Target target = TARGETS.get(id);
        if (target == null || target.heads.isEmpty()) return null;
        int[] inside = INSIDE.get();
        if (inside[0] > 0) return null;
        Thread thread = Thread.currentThread();
        Call call = new Call(self, args, target, false, null);
        inside[0]++;
        try {
            for (Hook hook : target.heads) {
                if (hook.owner() != thread) continue;
                hook.run(call);
                if (call.cancelled) return new Cancelled(fit(call.value, target.returns));
            }
        } finally {
            inside[0]--;
        }
        return null;
    }

    public static Object tail(String id, Object self, Object[] args, Object result) {
        Target target = TARGETS.get(id);
        if (target == null || target.tails.isEmpty()) return result;
        int[] inside = INSIDE.get();
        if (inside[0] > 0) return result;
        Thread thread = Thread.currentThread();
        Call call = new Call(self, args, target, true, result);
        inside[0]++;
        try {
            for (Hook hook : target.tails) {
                if (hook.owner() == thread) hook.run(call);
            }
        } finally {
            inside[0]--;
        }
        return fit(call.value, target.returns);
    }

    public static Object cancelled(Object entered) {
        return ((Cancelled) entered).value;
    }

    private static Object fit(Object value, Class<?> type) {
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
}
