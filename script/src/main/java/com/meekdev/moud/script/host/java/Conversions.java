package com.meekdev.moud.script.host.java;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class Conversions {

    private record Out(Class<?> type, Function<Object, Object> convert) {}

    private record In(Class<?> type, BiFunction<Object, Class<?>, Object> convert) {}

    private static final List<Out> OUTS = new CopyOnWriteArrayList<>();
    private static final List<In> INS = new CopyOnWriteArrayList<>();
    private static final Map<Class<?>, Out> OUT_CACHE = new ConcurrentHashMap<>();
    private static final Out NONE = new Out(Void.class, value -> value);

    private Conversions() {}

    @SuppressWarnings("unchecked")
    public static <T> void register(Class<T> type, Function<T, Object> toScript, BiFunction<Object, Class<?>, T> toJava) {
        OUTS.removeIf(out -> out.type() == type);
        INS.removeIf(in -> in.type() == type);
        OUTS.add(new Out(type, value -> toScript.apply((T) value)));
        INS.add(new In(type, (BiFunction<Object, Class<?>, Object>) (BiFunction<?, ?, ?>) toJava));
        OUT_CACHE.clear();
    }

    public static Object toScript(Object value) {
        if (value == null) return null;
        Out out = OUT_CACHE.computeIfAbsent(value.getClass(), type -> {
            for (Out candidate : OUTS) {
                if (candidate.type().isAssignableFrom(type)) return candidate;
            }
            return NONE;
        });
        if (out == NONE) return JavaLibrary.wrap(value);
        Object converted = out.convert().apply(value);
        return converted == null ? JavaLibrary.wrap(value) : converted;
    }

    static Object toJava(Object value, Class<?> want) {
        if (INS.isEmpty() || value == null || want == Object.class || want.isInstance(value)) return JavaLibrary.MISMATCH;
        for (In in : INS) {
            if (!want.isAssignableFrom(in.type()) && !in.type().isAssignableFrom(want)) continue;
            Object converted = in.convert().apply(value, want);
            if (converted != null && want.isInstance(converted)) return converted;
        }
        return JavaLibrary.MISMATCH;
    }
}
