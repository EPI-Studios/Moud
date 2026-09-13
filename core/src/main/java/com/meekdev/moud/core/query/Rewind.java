package com.meekdev.moud.core.query;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.part.Part;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class Rewind {

    public static final int TICKS_PER_SECOND = 20;

    public static final int WINDOW = TICKS_PER_SECOND;

    private record Sample(long tick, CFrame frame) {}

    private final Map<Integer, ArrayDeque<Sample>> history = new HashMap<>();
    private final Set<Integer> watched = new HashSet<>();
    private long tick;

    public void moved(Instance instance) {
        if (instance instanceof Part part) watched.add(part.id());
        for (Instance child : instance.children()) moved(child);
    }

    public void record(InstanceTree tree) {
        tick++;
        for (Iterator<Integer> it = watched.iterator(); it.hasNext(); ) {
            int id = it.next();
            if (!(tree.byId(id) instanceof Part part)) {
                it.remove();
                history.remove(id);
                continue;
            }
            CFrame now = Transforms.world(part);
            ArrayDeque<Sample> samples = history.computeIfAbsent(id, key -> new ArrayDeque<>());
            if (samples.isEmpty() || !samples.peekLast().frame().equals(now)) samples.addLast(new Sample(tick, now));
            while (samples.size() >= 2 && secondOf(samples).tick() <= tick - WINDOW) samples.removeFirst();
        }
    }

    public void clear() {
        history.clear();
        watched.clear();
    }

    public double now() {
        return (double) tick / TICKS_PER_SECOND;
    }

    public CFrame at(Part part, double seconds) {
        ArrayDeque<Sample> samples = history.get(part.id());
        if (samples == null || samples.isEmpty()) return Transforms.world(part);
        double when = seconds * TICKS_PER_SECOND;
        Sample before = null;
        for (Sample sample : samples) {
            if (sample.tick() > when) {
                if (before == null) return sample.frame();
                double from = Math.max(before.tick(), sample.tick() - 1);
                if (when <= from) return before.frame();
                return before.frame().lerp(sample.frame(), (when - from) / (sample.tick() - from));
            }
            before = sample;
        }
        return Transforms.world(part);
    }

    private static Sample secondOf(ArrayDeque<Sample> samples) {
        Iterator<Sample> it = samples.iterator();
        it.next();
        return it.next();
    }
}
