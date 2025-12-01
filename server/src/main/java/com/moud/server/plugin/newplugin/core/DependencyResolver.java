package com.moud.server.plugin.newplugin.core;

import java.util.*;

public class DependencyResolver {

    /** Retourne les descriptions triées, ou lève une IllegalStateException si cycle. */
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

    private static void visit(PluginContainer pc,
                              Map<String, PluginContainer> byName,
                              List<PluginContainer> sorted,
                              Set<String> tempMark,
                              Set<String> permMark) {

        String key = pc.getDescription().name.toLowerCase();
        if (permMark.contains(key)) return;
        if (!tempMark.add(key))
            throw new IllegalStateException("Cycle dans les dépendances concernant " + key);

        for (String dep : pc.getDescription().depends) {
            String name = PluginDescription.depName(dep).toLowerCase();
            PluginContainer child = byName.get(name);
            if (child == null)
                throw new IllegalStateException("Dépendance manquante : " + dep);
            visit(child, byName, sorted, tempMark, permMark);
        }
        tempMark.remove(key);
        permMark.add(key);
        sorted.add(pc);
    }
}