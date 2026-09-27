package com.meekdev.moud.core.space;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Aabb;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Broadphase {

    private static final long WIDEST = 4096;

    private final double cell;
    private final Map<Long, List<Instance>> cells = new HashMap<>();
    private final Map<Instance, Entry> entries = new IdentityHashMap<>();
    private final List<Instance> wide = new ArrayList<>();

    private int query;

    public Broadphase(double cell) {
        this.cell = cell;
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        cells.clear();
        entries.clear();
        wide.clear();
    }

    public boolean put(Instance instance, Aabb box) {
        Entry current = entries.get(instance);
        if (current != null && current.box.equals(box)) return false;
        Entry entry = new Entry(box);
        if (current != null && current.sameCells(entry)) {
            current.box = box;
            return true;
        }
        remove(instance);
        entries.put(instance, entry);
        if (entry.wide) wide.add(instance);
        else entry.forEachCell(key -> cells.computeIfAbsent(key, k -> new ArrayList<>(4)).add(instance));
        return true;
    }

    public boolean remove(Instance instance) {
        Entry old = entries.remove(instance);
        if (old == null) return false;
        if (old.wide) {
            wide.remove(instance);
            return true;
        }
        old.forEachCell(key -> {
            List<Instance> list = cells.get(key);
            if (list == null) return;
            list.remove(instance);
            if (list.isEmpty()) cells.remove(key);
        });
        return true;
    }

    public void rebuild(List<? extends Instance> instances, Function<Instance, Aabb> box) {
        clear();
        for (Instance instance : instances) {
            Aabb b = box.apply(instance);
            if (b != null) put(instance, b);
        }
    }

    public void query(Aabb region, Consumer<Instance> out) {
        int stamp = ++query;
        if (!wide.isEmpty()) report(wide, region, stamp, out);
        int minX = floor(region.minX());
        int maxX = floor(region.maxX());
        int minY = floor(region.minY());
        int maxY = floor(region.maxY());
        int minZ = floor(region.minZ());
        int maxZ = floor(region.maxZ());

        if (cellSpan(minX, maxX, minY, maxY, minZ, maxZ) > cells.size()) {
            for (List<Instance> list : cells.values()) {
                report(list, region, stamp, out);
            }
            return;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    List<Instance> list = cells.get(key(x, y, z));
                    if (list != null) report(list, region, stamp, out);
                }
            }
        }
    }

    private void report(List<Instance> list, Aabb region, int stamp, Consumer<Instance> out) {
        for (int n = 0; n < list.size(); n++) {
            Instance instance = list.get(n);
            Entry entry = entries.get(instance);
            if (entry == null || entry.seen == stamp) continue;
            entry.seen = stamp;
            if (entry.box.intersects(region)) out.accept(instance);
        }
    }

    private long cellSpan(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        return cellSpan(minX, maxX, minY, maxY, minZ, maxZ, cells.size());
    }

    private static long cellSpan(int minX, int maxX, int minY, int maxY, int minZ, int maxZ, long limit) {
        long span = axis(minX, maxX);
        if (span > limit) return span;
        span *= axis(minY, maxY);
        if (span > limit) return span;
        return span * axis(minZ, maxZ);
    }

    private static long axis(int min, int max) {
        return max < min ? 0 : (long) max - min + 1;
    }

    public Aabb boundsOf(Instance instance) {
        Entry entry = entries.get(instance);
        return entry == null ? null : entry.box;
    }

    private int floor(double v) {
        return (int) Math.floor(v / cell);
    }

    private static long key(int x, int y, int z) {
        return (x & 0x1FFFFFL) | ((y & 0x1FFFFFL) << 21) | ((z & 0x1FFFFFL) << 42);
    }

    private final class Entry {
        Aabb box;
        final boolean wide;
        final int minX;
        final int minY;
        final int minZ;
        final int maxX;
        final int maxY;
        final int maxZ;
        int seen;

        Entry(Aabb box) {
            this.box = box;
            minX = floor(box.minX());
            minY = floor(box.minY());
            minZ = floor(box.minZ());
            maxX = floor(box.maxX());
            maxY = floor(box.maxY());
            maxZ = floor(box.maxZ());
            wide = cellSpan(minX, maxX, minY, maxY, minZ, maxZ, WIDEST) > WIDEST;
        }

        boolean sameCells(Entry other) {
            return wide == other.wide && minX == other.minX && minY == other.minY && minZ == other.minZ
                    && maxX == other.maxX && maxY == other.maxY && maxZ == other.maxZ;
        }

        void forEachCell(Consumer<Long> out) {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        out.accept(key(x, y, z));
                    }
                }
            }
        }
    }
}
