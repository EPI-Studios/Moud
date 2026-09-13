package com.meekdev.moud.core.query;

import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.part.Part;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class Touches {

    public static final double MARGIN = 0.01;

    private static final Map<InstanceTree, Touches> TREES = new WeakHashMap<>();

    private final Map<Part, Set<Part>> touching = new HashMap<>();

    private Touches() {}

    public static void step(InstanceTree tree) {
        TREES.computeIfAbsent(tree, t -> new Touches()).run(tree);
    }

    private void run(InstanceTree tree) {
        List<Part> listening = new ArrayList<>();
        for (Part part : tree.ofClass(Classes.PART)) {
            if (takesPart(part) && (part.touched.count() > 0 || part.touchEnded.count() > 0)) listening.add(part);
        }
        if (listening.isEmpty() && touching.isEmpty()) return;

        Map<Part, Set<Part>> next = new HashMap<>();
        List<Part> near = new ArrayList<>();
        for (Part part : listening) {
            Queries.Box box = Queries.Box.of(part);
            near.clear();
            tree.spatial().candidates(SpatialIndex.bounds(Transforms.world(part), part.size).grow(MARGIN), near);
            Set<Part> now = new HashSet<>();
            for (Part other : near) {
                if (other == part || !takesPart(other) || Queries.isUnder(other, part) || Queries.isUnder(part, other)) continue;
                if (Queries.overlap(box, Queries.Box.of(other), MARGIN)) now.add(other);
            }
            next.put(part, now);
        }

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
