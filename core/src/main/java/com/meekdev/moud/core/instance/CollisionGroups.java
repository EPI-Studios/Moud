package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Classes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// the collision groups in a tree as the bits the physics filters by. bit 0 is the world itself, which
// every group collides with, bit 1 marks a living capsule, and each group after that has a bit of its own
public final class CollisionGroups {

    public static final String DEFAULT = "default";
    public static final long WORLD = 1L;
    public static final long CAPSULE = 2L;
    public static final int MOST = 32;

    private final Map<String, Integer> index = new LinkedHashMap<>();
    private final Map<String, Set<String>> ignores = new LinkedHashMap<>();

    private CollisionGroups() {}

    public static CollisionGroups of(InstanceTree tree) {
        CollisionGroups groups = new CollisionGroups();
        groups.index.put(DEFAULT, 0);
        groups.ignores.put(DEFAULT, new HashSet<>());
        // by name, because the server builds a body's filter and the client builds the boxes it sweeps
        // against, and the two only agree on a bit if they number the groups the same way
        List<CollisionGroup> sorted = new ArrayList<>(tree.ofClass(Classes.COLLISION_GROUP));
        sorted.sort(Comparator.comparing(Instance::name));
        for (CollisionGroup group : sorted) {
            String name = group.name();
            if (!groups.index.containsKey(name)) {
                if (groups.index.size() >= MOST) continue;
                groups.index.put(name, groups.index.size());
            }
            Set<String> set = groups.ignores.computeIfAbsent(name, n -> new HashSet<>());
            for (String other : group.ignores.split(",")) {
                if (!other.isBlank()) set.add(other.trim());
            }
        }
        return groups;
    }

    // the group a part collides as. a limb says nothing of its own and moves with the body it is on
    public static String groupOf(Part part) {
        if (!DEFAULT.equals(part.collisionGroup)) return part.collisionGroup;
        for (Instance at = part.parent(); at != null; at = at.parent()) {
            if (at instanceof Character body) return body.collisionGroup;
        }
        return DEFAULT;
    }

    public boolean known(String group) {
        return index.containsKey(group);
    }

    public List<String> names() {
        return new ArrayList<>(index.keySet());
    }

    // an unknown name is the default group, so a typo collides like everything else rather than with nothing
    public long category(String group) {
        return 1L << (2 + index.getOrDefault(group, 0));
    }

    public boolean collide(String a, String b) {
        String one = known(a) ? a : DEFAULT;
        String two = known(b) ? b : DEFAULT;
        return !ignores.getOrDefault(one, Set.of()).contains(two) && !ignores.getOrDefault(two, Set.of()).contains(one);
    }

    // what a member of this group collides with: the world, and every group it does not ignore
    public long mask(String group) {
        long mask = WORLD;
        for (String other : index.keySet()) {
            if (collide(group, other)) mask |= category(other);
        }
        return mask;
    }
}
