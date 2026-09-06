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
        for (int x = floor(region.minX()); x <= floor(region.maxX()); x++) {
            for (int y = floor(region.minY()); y <= floor(region.maxY()); y++) {
                for (int z = floor(region.minZ()); z <= floor(region.maxZ()); z++) {
                    List<Instance> list = cells.get(key(x, y, z));
                    if (list == null) continue;
                    for (int n = 0; n < list.size(); n++) {
                        Instance instance = list.get(n);
                        Entry entry = entries.get(instance);
                        if (entry == null || entry.seen == stamp) continue;
                        entry.seen = stamp;
                        if (entry.box.intersects(region)) out.accept(instance);
                    }
                }
            }
        }
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
