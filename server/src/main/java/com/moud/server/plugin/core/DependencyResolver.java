package com.moud.server.plugin.core;

import java.util.*;

public class DependencyResolver {

    /**
     * Return the sorted plugin containers, or throw an IllegalStateException if there is a cycle.
     * */
    public static List<PluginContainer> sort(Collection<PluginContainer> containers) {
        Map<String, PluginContainer> byName = new HashMap<>();
        for (var pc : containers) byName.put(pc.getDescription().name.toLowerCase(), pc);

        List<PluginContainer> sorted = new ArrayList<>();
        Set<String> tempMark = new HashSet<>();
        Set<String> permMark = new HashSet<>();

        for (var pc : containers)
            visit(pc, byName, sorted, tempMark, permMark);

        return sorted;
    }

    /**
     * Helper method for topological sort using depth-first search.
     * @param pc       The current plugin container being visited
     * @param byName   Map of plugin names to their containers
     * @param sorted   The list to store the sorted plugin containers
     * @param tempMark Sets to track temporary and permanent marks for cycle detection
     * @param permMark Sets to track temporary and permanent marks for cycle detection
     */
    private static void visit(PluginContainer pc,
                              Map<String, PluginContainer> byName,
                              List<PluginContainer> sorted,
                              Set<String> tempMark,
                              Set<String> permMark) {

        String key = pc.getDescription().name.toLowerCase();
        if (permMark.contains(key)) return;
        if (!tempMark.add(key))
            throw new IllegalStateException("Loop detected in dependencies involving " + key);

        for (String dep : pc.getDescription().depends) {
            String name = PluginDescription.depName(dep).toLowerCase();
            PluginContainer child = byName.get(name);
            if (child == null)
                throw new IllegalStateException("Dependency missing: " + dep);
            visit(child, byName, sorted, tempMark, permMark);
        }
        tempMark.remove(key);
        permMark.add(key);
        sorted.add(pc);
    }
}