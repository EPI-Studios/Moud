package com.meekdev.moud.core.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.DoubleSupplier;

public final class MemoryQueue {

    public record Read(String id, List<String> values) {}

    private static final class Item {
        private final long order;
        private final String value;
        private final double priority;
        private final double expires;
        private double hiddenUntil = Double.NEGATIVE_INFINITY;
        private String read;

        private Item(long order, String value, double priority, double expires) {
            this.order = order;
            this.value = value;
            this.priority = priority;
            this.expires = expires;
        }
    }

    private final DoubleSupplier now;
    private final NavigableSet<Item> items = new TreeSet<>(Comparator.<Item>comparingDouble(item -> -item.priority).thenComparingLong(item -> item.order));
    private long added;
    private long reads;

    MemoryQueue(DoubleSupplier now) {
        this.now = now;
    }

    public synchronized void add(String value, double expiration, double priority) {
        double at = now.getAsDouble();
        items.removeIf(item -> item.expires <= at);
        items.add(new Item(added++, value, priority, at + expiration));
    }

    public synchronized Read read(int count, boolean allOrNothing, double invisibility) {
        double at = now.getAsDouble();
        items.removeIf(item -> item.expires <= at);
        List<Item> taken = new ArrayList<>();
        for (Item item : items) {
            if (taken.size() >= count) break;
            if (item.hiddenUntil <= at) taken.add(item);
        }
        if (taken.isEmpty() || allOrNothing && taken.size() < count) return null;
        String id = Long.toHexString(++reads);
        List<String> values = new ArrayList<>(taken.size());
        for (Item item : taken) {
            item.hiddenUntil = at + invisibility;
            item.read = id;
            values.add(item.value);
        }
        return new Read(id, values);
    }

    public synchronized int remove(String id) {
        double at = now.getAsDouble();
        int before = items.size();
        items.removeIf(item -> id.equals(item.read) && item.hiddenUntil > at);
        return before - items.size();
    }

    public synchronized int size(boolean excludeInvisible) {
        double at = now.getAsDouble();
        items.removeIf(item -> item.expires <= at);
        if (!excludeInvisible) return items.size();
        int visible = 0;
        for (Item item : items) {
            if (item.hiddenUntil <= at) visible++;
        }
        return visible;
    }
}
