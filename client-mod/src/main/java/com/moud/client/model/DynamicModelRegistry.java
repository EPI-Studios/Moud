package com.moud.client.model;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DynamicModelRegistry {
    private static final Set<Identifier> REGISTERED_MODELS = ConcurrentHashMap.newKeySet();
    private static final Set<String> REGISTERED_NAMESPACES = ConcurrentHashMap.newKeySet();

    private DynamicModelRegistry() {}

    public static void registerModel(Identifier identifier) {
        if (identifier != null) {
            REGISTERED_MODELS.add(identifier);
            REGISTERED_NAMESPACES.add(identifier.getNamespace());
        }
    }

    public static Collection<Identifier> getRegisteredModels() {
        return Collections.unmodifiableCollection(REGISTERED_MODELS);
    }

    public static boolean hasNamespace(String namespace) {
        return REGISTERED_NAMESPACES.contains(namespace);
    }

    public static void clear() {
        REGISTERED_MODELS.clear();
        REGISTERED_NAMESPACES.clear();
    }
}
