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

    private final double cell;
    private final Map<Long, List<Instance>> cells = new HashMap<>();
    private final Map<Instance, Entry> entries = new IdentityHashMap<>();

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
    }

    public boolean put(Instance instance, Aabb box) {
        Entry current = entries.get(instance);
        if (current != null && current.box.equals(box)) return false;
        remove(instance);
        entries.put(instance, new Entry(box));
        forEachCell(box, key -> cells.computeIfAbsent(key, k -> new ArrayList<>(4)).add(instance));
        return true;
    }

    public boolean remove(Instance instance) {
        Entry old = entries.remove(instance);
        if (old == null) return false;
        forEachCell(old.box, key -> {
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
        long span = axis(minX, maxX);
        if (span > cells.size()) return span;
        span *= axis(minY, maxY);
        if (span > cells.size()) return span;
        return span * axis(minZ, maxZ);
    }

    private static long axis(int min, int max) {
        return max < min ? 0 : (long) max - min + 1;
    }

    public Aabb boundsOf(Instance instance) {
        Entry entry = entries.get(instance);
        return entry == null ? null : entry.box;
    }

    private void forEachCell(Aabb box, Consumer<Long> out) {
        for (int x = floor(box.minX()); x <= floor(box.maxX()); x++) {
            for (int y = floor(box.minY()); y <= floor(box.maxY()); y++) {
                for (int z = floor(box.minZ()); z <= floor(box.maxZ()); z++) {
                    out.accept(key(x, y, z));
                }
            }
        }
    }

    private int floor(double v) {
        return (int) Math.floor(v / cell);
    }

    private static long key(int x, int y, int z) {
        return (x & 0x1FFFFFL) | ((y & 0x1FFFFFL) << 21) | ((z & 0x1FFFFFL) << 42);
    }

    private static final class Entry {
        final Aabb box;
        int seen;

        Entry(Aabb box) {
            this.box = box;
        }
    }
}
