package com.meekdev.moud.script.host;

import com.meekdev.moud.core.instance.Instance;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Profiler {

    private static final class Entry {
        long nanos;
        long calls;
        long worst;
    }

    private Map<String, Entry> times = new HashMap<>();
    private long spent;
    private int depth;

    public void add(String where, long nanos) {
        Entry entry = times.computeIfAbsent(where, key -> new Entry());
        entry.nanos += nanos;
        entry.calls++;
        entry.worst = Math.max(entry.worst, nanos);
    }

    public void enter() {
        depth++;
    }

    public void leave(long nanos) {
        if (--depth == 0) spent += nanos;
    }

    public long spent() {
        return spent;
    }

    public Map<String, Object> take() {
        Map<String, Entry> taken = times;
        times = new HashMap<>();
        Map<String, Object> out = new LinkedHashMap<>();
        taken.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().nanos, a.getValue().nanos))
                .forEach(e -> out.put(e.getKey(), Map.of("milliseconds", e.getValue().nanos / 1e6,
                        "calls", (double) e.getValue().calls, "worst", e.getValue().worst / 1e6)));
        return out;
    }

    static String owner(Object owner) {
        return owner instanceof Instance script ? script.name() : "main";
    }
}
