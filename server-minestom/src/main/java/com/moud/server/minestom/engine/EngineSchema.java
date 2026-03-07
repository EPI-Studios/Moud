package com.moud.server.minestom.engine;


import com.moud.core.NodeTypeProviders;
import com.moud.core.NodeTypeRegistry;
import com.moud.server.minestom.engine.EngineSchema;

public final class EngineSchema {
    private EngineSchema() {
    }

    public static NodeTypeRegistry createDefault() {
        NodeTypeRegistry registry = new NodeTypeRegistry()
                .setAllowUnknownTypes(true)
                .setAllowUnknownProperties(true);
        NodeTypeProviders.loadInto(registry);
        return registry;
    }
}
