package com.meekdev.moud.core.memory;

import com.meekdev.moud.core.time.Clock;
import java.util.HashMap;
import java.util.Map;

public final class MemoryStores {

    public static final double LONGEST = 3_888_000;

    private final Clock.Source clock;
    private final long started;
    private final Map<String, MemoryHash> hashes = new HashMap<>();
    private final Map<String, MemorySorted> sorted = new HashMap<>();
    private final Map<String, MemoryQueue> queues = new HashMap<>();

    public MemoryStores() {
        this(System::nanoTime);
    }

    public MemoryStores(Clock.Source clock) {
        this.clock = clock;
        this.started = clock.nanos();
    }

    public synchronized MemoryHash hash(String name) {
        return hashes.computeIfAbsent(name, n -> new MemoryHash(this::now));
    }

    public synchronized MemorySorted sorted(String name) {
        return sorted.computeIfAbsent(name, n -> new MemorySorted(this::now));
    }

    public synchronized MemoryQueue queue(String name) {
        return queues.computeIfAbsent(name, n -> new MemoryQueue(this::now));
    }

    public synchronized void clear() {
        hashes.clear();
        sorted.clear();
        queues.clear();
    }

    double now() {
        return (clock.nanos() - started) * 1e-9;
    }
}
