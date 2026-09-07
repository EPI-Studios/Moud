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

// a uniform grid, because a collision query that scans every part is worse than no collision at
// all once a place has more than a few thousand of them
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

    public void put(Instance instance, Aabb box) {
        remove(instance);
        entries.put(instance, new Entry(box));
        forEachCell(box, key -> cells.computeIfAbsent(key, k -> new ArrayList<>(4)).add(instance));
    }

    public void remove(Instance instance) {
        Entry old = entries.remove(instance);
        if (old == null) return;
        forEachCell(old.box, key -> {
            List<Instance> list = cells.get(key);
            if (list == null) return;
            list.remove(instance);
            if (list.isEmpty()) cells.remove(key);
        });
    }

    public void rebuild(List<? extends Instance> instances, Function<Instance, Aabb> box) {
        clear();
        for (Instance instance : instances) {
            Aabb b = box.apply(instance);
            if (b != null) put(instance, b);
        }
    }

    // an instance sits in every cell it overlaps, so a wide query reaches the same one many times.
    // the stamp is what makes each one reported once, without allocating a set per query
    public void query(Aabb region, Consumer<Instance> out) {
        int stamp = ++query;
        int minX = floor(region.minX());
        int maxX = floor(region.maxX());
        int minY = floor(region.minY());
        int maxY = floor(region.maxY());
        int minZ = floor(region.minZ());
        int maxZ = floor(region.maxZ());

        // a caller may ask for the whole world, and walking a region that size costs far more than
        // there are parts to report: baking every static collider once asked for thirty million
        // metres a side, which is a hundred million billion cells and never returned. above the
        // crossover, what is occupied is the smaller set to walk
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

    // multiplied one axis at a time and abandoned as soon as it is past the cell count, so a
    // region wide enough to overflow the product never gets that far
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
