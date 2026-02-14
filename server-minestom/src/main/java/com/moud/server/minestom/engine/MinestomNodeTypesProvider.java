package com.moud.server.minestom.engine;

import com.moud.core.*;
import com.moud.server.minestom.engine.nodes.RootNode;
import com.moud.server.minestom.engine.nodes.TickerNode;

import java.util.Map;

public final class MinestomNodeTypesProvider implements NodeTypeProvider {
    @Override
    public int order() {
        return 100;
    }

    @Override
    public void register(NodeTypeRegistry registry) {
        registry.registerType(new NodeTypeDef("Root", "Root", "Minestom", 100, Map.of()));
        registry.registerType(new NodeTypeDef("Ticker", "Ticker", "Minestom", 110, Map.of(
                "ticks", new PropertyDef("ticks", PropertyType.INT, "0", "Ticks", "Runtime", 0, Map.of())
        )));

        registry.registerClass(RootNode.class, "Root");
        registry.registerClass(TickerNode.class, "Ticker");
    }
}
