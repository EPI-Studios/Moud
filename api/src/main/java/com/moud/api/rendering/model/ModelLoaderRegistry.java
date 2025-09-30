package com.moud.api.rendering.model;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of model loaders keyed by file extension.
 */
public final class ModelLoaderRegistry {
    private final Map<String, ModelLoader> loaders = new ConcurrentHashMap<>();

    public Optional<ModelLoader> find(String extension) {
        if (extension == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(loaders.get(extension.toLowerCase()));
    }

    public ModelLoader require(String extension) {
        return find(extension).orElseThrow(() -> new IllegalArgumentException("No loader for extension: " + extension));
    }

    public void register(String extension, ModelLoader loader) {
        loaders.put(extension.toLowerCase(), loader);
    }

    public void unregister(String extension) {
        if (extension != null) {
            loaders.remove(extension.toLowerCase());
        }
    }
}
