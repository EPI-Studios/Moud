package com.meekdev.moud.core.clazz;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClassRegistry {

    private final Map<String, ClassDef<?>> byName = new LinkedHashMap<>();

    public void register(ClassDef<?> def) {
        ClassDef<?> existing = byName.putIfAbsent(def.name(), def);
        if (existing != null && existing != def) {
            throw new IllegalStateException("class '" + def.name() + "' registered twice");
        }
    }

    public ClassDef<?> find(String name) {
        return byName.get(name);
    }

    public Collection<ClassDef<?>> all() {
        return byName.values();
    }

    public ClassDef<?> require(String name) {
        ClassDef<?> def = byName.get(name);
        if (def != null) return def;
        throw new IllegalArgumentException("unknown class '" + name + "', known classes are "
                + String.join(", ", byName.keySet()));
    }
}
