package com.meekdev.moud.core.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.DoubleSupplier;
import java.util.function.UnaryOperator;

public final class MemoryHash {

    private record Entry(String value, double expires) {}

    private final DoubleSupplier now;
    private final NavigableMap<String, Entry> entries = new TreeMap<>();
    private double swept;

    MemoryHash(DoubleSupplier now) {
        this.now = now;
    }

    public synchronized String get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) return null;
        if (entry.expires <= now.getAsDouble()) {
            entries.remove(key);
            return null;
        }
        return entry.value;
    }

    public synchronized void set(String key, String value, double expiration) {
        double at = now.getAsDouble();
        sweep(at, false);
        entries.put(key, new Entry(value, at + expiration));
    }

    public String update(String key, UnaryOperator<String> change, double expiration) {
        String next = change.apply(get(key));
        if (next != null) set(key, next, expiration);
        return next;
    }

    public synchronized void remove(String key) {
        entries.remove(key);
    }

    public synchronized List<String> keys(int limit, String after) {
        double at = now.getAsDouble();
        sweep(at, false);
        Map<String, Entry> page = after == null ? entries : entries.tailMap(after, false);
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, Entry> entry : page.entrySet()) {
            if (keys.size() >= limit) break;
            if (entry.getValue().expires > at) keys.add(entry.getKey());
        }
        return keys;
    }

    public synchronized int size() {
        sweep(now.getAsDouble(), true);
        return entries.size();
    }

    private void sweep(double at, boolean always) {
        if (!always && at < swept) return;
        swept = at + 1;
        entries.values().removeIf(entry -> entry.expires <= at);
    }
}
