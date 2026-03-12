package com.moud.server.minestom.engine;


import com.moud.core.NodeTypeDef;
import com.moud.core.PropertyDef;
import com.moud.core.PropertyType;
import java.util.Map;
import com.moud.core.*;
import com.moud.core.scene.Model3D;
import com.moud.server.minestom.engine.nodes.RootNode;
import com.moud.server.minestom.engine.nodes.TickerNode;

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
        registry.registerType(new NodeTypeDef("Model3D", "Model 3D", "Scene", 200, Map.of(
                Model3D.PROP_MODEL_PATH,      new PropertyDef(Model3D.PROP_MODEL_PATH,      PropertyType.STRING, "",     "Model Path",    "Model", 0, Map.of()),
                Model3D.PROP_ANIMATION,       new PropertyDef(Model3D.PROP_ANIMATION,       PropertyType.STRING, "",     "Animation",     "Model", 1, Map.of()),
                Model3D.PROP_ANIMATION_LOOP,  new PropertyDef(Model3D.PROP_ANIMATION_LOOP,  PropertyType.STRING, "loop", "Loop Mode",     "Model", 2, Map.of()),
                Model3D.PROP_ANIMATION_SPEED, new PropertyDef(Model3D.PROP_ANIMATION_SPEED, PropertyType.FLOAT,  "1.0",  "Anim Speed",    "Model", 3, Map.of())
        )));

        registry.registerClass(RootNode.class, "Root");
        registry.registerClass(TickerNode.class, "Ticker");
        registry.registerClass(Model3D.class, "Model3D");
    }
}
