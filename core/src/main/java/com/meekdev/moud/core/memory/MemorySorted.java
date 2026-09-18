package com.meekdev.moud.core.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.DoubleSupplier;
import java.util.function.UnaryOperator;

public final class MemorySorted {

    public record Item(String key, String value, Object sortKey) {}

    public record Bound(String key, Object sortKey) {}

    private record Entry(Item item, double expires) {}

    private static final Comparator<Item> ORDER = Comparator.<Item>comparingInt(item -> rank(item.sortKey()))
            .thenComparing((a, b) -> sameRank(a.sortKey(), b.sortKey()))
            .thenComparing(Item::key);

    private final DoubleSupplier now;
    private final Map<String, Entry> entries = new HashMap<>();
    private final NavigableSet<Item> order = new TreeSet<>(ORDER);
    private double swept;

    MemorySorted(DoubleSupplier now) {
        this.now = now;
    }

    public synchronized Item get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) return null;
        if (entry.expires <= now.getAsDouble()) {
            drop(key);
            return null;
        }
        return entry.item;
    }

    public synchronized boolean set(String key, String value, Object sortKey, double expiration) {
        double at = now.getAsDouble();
        sweep(at, false);
        boolean fresh = get(key) == null;
        drop(key);
        Item item = new Item(key, value, sortKey);
        entries.put(key, new Entry(item, at + expiration));
        order.add(item);
        return fresh;
    }

    public Item update(String key, UnaryOperator<Item> change, double expiration) {
        Item next = change.apply(get(key));
        if (next == null) return null;
        set(key, next.value(), next.sortKey(), expiration);
        return get(key);
    }

    public synchronized void remove(String key) {
        drop(key);
    }

    public synchronized List<Item> range(boolean ascending, int count, Bound lower, Bound upper) {
        sweep(now.getAsDouble(), true);
        List<Item> out = new ArrayList<>();
        for (Item item : ascending ? order : order.descendingSet()) {
            if (out.size() >= count) break;
            if (lower != null && against(item, lower, true) <= 0) continue;
            if (upper != null && against(item, upper, false) >= 0) continue;
            out.add(item);
        }
        return out;
    }

    public synchronized int size() {
        sweep(now.getAsDouble(), true);
        return entries.size();
    }

    private void drop(String key) {
        Entry old = entries.remove(key);
        if (old != null) order.remove(old.item);
    }

    private void sweep(double at, boolean always) {
        if (!always && at < swept) return;
        swept = at + 1;
        List<String> gone = new ArrayList<>();
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            if (entry.getValue().expires <= at) gone.add(entry.getKey());
        }
        for (String key : gone) drop(key);
    }

    private static int against(Item item, Bound bound, boolean lower) {
        int sorted = Integer.compare(rank(item.sortKey()), rank(bound.sortKey()));
        if (sorted == 0) sorted = sameRank(item.sortKey(), bound.sortKey());
        if (sorted != 0) return sorted;
        if (bound.key() == null) return lower ? -1 : 1;
        return item.key().compareTo(bound.key());
    }

    private static int rank(Object sortKey) {
        return switch (sortKey) {
            case Number n -> 0;
            case String s -> 1;
            case null, default -> 2;
        };
    }

    private static int sameRank(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) return Double.compare(x.doubleValue(), y.doubleValue());
        if (a instanceof String x && b instanceof String y) return x.compareTo(y);
        return 0;
    }
}
