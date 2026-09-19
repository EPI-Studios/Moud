package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Attachment;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.physics.Constraint;
import com.meekdev.moud.core.physics.WeldConstraint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Assemblies {

    private static Map<Part, List<Part>> groups = Map.of();

    private Assemblies() {}

    public static void build(InstanceTree tree) {
        Map<Part, Part> leader = new HashMap<>();
        for (WeldConstraint weld : tree.ofClass(Classes.WELD_CONSTRAINT)) {
            if (weld.enabled && weld.part0 instanceof Part a && weld.part1 instanceof Part b) join(leader, a, b);
        }
        for (Constraint constraint : tree.ofClass(Classes.CONSTRAINT)) {
            if (constraint.enabled && constraint.attachment0 instanceof Attachment a0 && constraint.attachment1 instanceof Attachment a1
                    && a0.parent() instanceof Part a && a1.parent() instanceof Part b) {
                join(leader, a, b);
            }
        }
        Map<Part, List<Part>> built = new HashMap<>();
        for (Part part : leader.keySet()) {
            if (!part.isAlive()) continue;
            List<Part> group = built.computeIfAbsent(find(leader, part), root -> new ArrayList<>());
            group.add(part);
        }
        Map<Part, List<Part>> byPart = new HashMap<>();
        for (List<Part> group : built.values()) {
            List<Part> fixed = List.copyOf(group);
            for (Part part : fixed) byPart.put(part, fixed);
        }
        groups = byPart;
    }

    public static List<Part> of(Part part) {
        List<Part> group = groups.get(part);
        return group == null ? List.of(part) : group;
    }

    public static void clear() {
        groups = Map.of();
    }

    private static void join(Map<Part, Part> leader, Part a, Part b) {
        if (a == b) return;
        leader.putIfAbsent(a, a);
        leader.putIfAbsent(b, b);
        Part ra = find(leader, a);
        Part rb = find(leader, b);
        if (ra != rb) leader.put(ra, rb);
    }

    private static Part find(Map<Part, Part> leader, Part part) {
        Part at = part;
        while (leader.get(at) != at) {
            Part up = leader.get(at);
            leader.put(at, leader.get(up));
            at = up;
        }
        return at;
    }
}
