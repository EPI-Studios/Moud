package com.moud.api.rendering.model;

import com.moud.api.rendering.model.loader.SimpleObjModelLoader;

/**
 * Provides a default registry for built-in model loaders.
 */
public final class ModelLoaders {
    private static final ModelLoaderRegistry REGISTRY = new ModelLoaderRegistry();

    static {
        REGISTRY.register("obj", new SimpleObjModelLoader());
    }

    private ModelLoaders() {
    }

    public static ModelLoaderRegistry registry() {
        return REGISTRY;
    }
}
