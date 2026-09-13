package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Classes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// which parts touch which, tick to tick, so touched and touchEnded fire on the changes
public final class Touches {

    // faces resting on each other count, and floating point never lands them exactly together
    public static final double MARGIN = 0.01;

    private static final Map<InstanceTree, Touches> TREES = new WeakHashMap<>();

    private final Map<Part, Set<Part>> touching = new HashMap<>();

    private Touches() {}

    public static void step(InstanceTree tree) {
        TREES.computeIfAbsent(tree, t -> new Touches()).run(tree);
    }

    private void run(InstanceTree tree) {
        List<Part> candidates = new ArrayList<>();
        List<Part> listening = new ArrayList<>();
        for (Part part : tree.ofClass(Classes.PART)) {
            if (!takesPart(part)) continue;
            candidates.add(part);
            if (part.touched.count() > 0 || part.touchEnded.count() > 0) listening.add(part);
        }

        // each box once per tick: a world frame is its whole parent chain composed
        Map<Part, Queries.Box> boxes = new HashMap<>();
        for (Part part : candidates) boxes.put(part, Queries.Box.of(part));

        Map<Part, Set<Part>> next = new HashMap<>();
        for (Part part : listening) {
            Queries.Box box = boxes.get(part);
            Set<Part> now = new HashSet<>();
            for (Part other : candidates) {
                if (other == part || Queries.isUnder(other, part) || Queries.isUnder(part, other)) continue;
                Queries.Box against = boxes.get(other);
                // far apart is most of them, and a distance between centres answers that for free
                double reach = box.half().length() + against.half().length() + MARGIN;
                if (box.centre().sub(against.centre()).lengthSq() > reach * reach) continue;
                if (Queries.overlap(box, against, MARGIN)) now.add(other);
            }
            next.put(part, now);
        }

        // swapped in before anything fires, so a handler that destroys a part finds a consistent state
        Map<Part, Set<Part>> before = new HashMap<>(touching);
        touching.clear();
        touching.putAll(next);

        for (Map.Entry<Part, Set<Part>> entry : next.entrySet()) {
            Part part = entry.getKey();
            Set<Part> was = before.getOrDefault(part, Set.of());
            for (Part other : entry.getValue()) {
                if (!was.contains(other) && part.isAlive()) part.touched.fire(other);
            }
            for (Part other : was) {
                if (!entry.getValue().contains(other) && part.isAlive()) part.touchEnded.fire(other);
            }
        }
    }

    private static boolean takesPart(Part part) {
        return part.canTouch && part.visible && !(Rig.OVERLAY.equals(part.name()) && part.parent() instanceof Part);
    }
}
