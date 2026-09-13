package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.instance.Instance;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.hollowcube.luau.LuaState;

public final class Profiler {

    private static final class Entry {
        long nanos;
        long calls;
        long worst;
    }

    private static final Map<LuaState, Map<String, Entry>> TIMES = new HashMap<>();

    private Profiler() {}

    public static void add(LuaState state, String where, long nanos) {
        Entry entry = TIMES.computeIfAbsent(state.mainThread(), key -> new HashMap<>()).computeIfAbsent(where, key -> new Entry());
        entry.nanos += nanos;
        entry.calls++;
        entry.worst = Math.max(entry.worst, nanos);
    }

    public static Map<String, Object> take(LuaState state) {
        Map<String, Entry> times = TIMES.remove(state.mainThread());
        Map<String, Object> out = new LinkedHashMap<>();
        if (times == null) return out;
        times.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue().nanos, a.getValue().nanos)).forEach(e ->
                out.put(e.getKey(), Map.of("milliseconds", e.getValue().nanos / 1e6, "calls", (double) e.getValue().calls,
                        "worst", e.getValue().worst / 1e6)));
        return out;
    }

    public static void forget(LuaState state) {
        TIMES.remove(state.mainThread());
    }

    static String owner(Object owner) {
        return owner instanceof Instance script ? script.name() : "main";
    }
}
