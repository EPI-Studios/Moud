package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.math.CFrame;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

// where parts were over the last second, on the server, so a hit can be tested against the world a
// player was looking at when they fired rather than the one that exists by the time the shot arrives
//
// only parts that have moved are kept. something that has never moved is where it always was, and a
// place's hundred thousand still parts cost nothing here. a moved part is sampled every tick after
// that, and a sample is only stored when the frame changed
public final class Rewind {

    public static final int TICKS_PER_SECOND = 20;

    // how far back a rewind can reach. past this a part is where its oldest sample puts it
    public static final int WINDOW = TICKS_PER_SECOND;

    private record Sample(long tick, CFrame frame) {}

    private final Map<Integer, ArrayDeque<Sample>> history = new HashMap<>();
    private final Set<Integer> watched = new HashSet<>();
    private long tick;

    // something in this branch moved, so every part under it is followed from now on
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
            // the oldest sample still inside the window has to stay, since it says where the part was then
            while (samples.size() >= 2 && secondOf(samples).tick() <= tick - WINDOW) samples.removeFirst();
        }
    }

    public void clear() {
        history.clear();
        watched.clear();
    }

    // in seconds, on the same clock as at
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
                // it held the earlier frame until the tick before the later one, then moved to it
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
