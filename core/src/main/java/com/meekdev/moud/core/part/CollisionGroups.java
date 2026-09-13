package com.meekdev.moud.core.part;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public long category(String group) {
        return 1L << (2 + index.getOrDefault(group, 0));
    }

    public boolean collide(String a, String b) {
        String one = known(a) ? a : DEFAULT;
        String two = known(b) ? b : DEFAULT;
        return !ignores.getOrDefault(one, Set.of()).contains(two) && !ignores.getOrDefault(two, Set.of()).contains(one);
    }

    public long mask(String group) {
        long mask = WORLD;
        for (String other : index.keySet()) {
            if (collide(group, other)) mask |= category(other);
        }
        return mask;
    }
}
