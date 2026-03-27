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
        registry.registerType(new NodeTypeDef("Model3D", "Model 3D", "Scene", 200, Map.ofEntries(
                Map.entry("x",                       new PropertyDef("x",                       PropertyType.FLOAT,  "0",    "X",             "Transform", 0,  Map.of("step", "0.1"))),
                Map.entry("y",                       new PropertyDef("y",                       PropertyType.FLOAT,  "0",    "Y",             "Transform", 1,  Map.of("step", "0.1"))),
                Map.entry("z",                       new PropertyDef("z",                       PropertyType.FLOAT,  "0",    "Z",             "Transform", 2,  Map.of("step", "0.1"))),
                Map.entry("rx",                      new PropertyDef("rx",                      PropertyType.FLOAT,  "0",    "Rot X",         "Transform", 10, Map.of("step", "1"))),
                Map.entry("ry",                      new PropertyDef("ry",                      PropertyType.FLOAT,  "0",    "Rot Y",         "Transform", 11, Map.of("step", "1"))),
                Map.entry("rz",                      new PropertyDef("rz",                      PropertyType.FLOAT,  "0",    "Rot Z",         "Transform", 12, Map.of("step", "1"))),
                Map.entry("sx",                      new PropertyDef("sx",                      PropertyType.FLOAT,  "1",    "Scale X",       "Transform", 20, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("sy",                      new PropertyDef("sy",                      PropertyType.FLOAT,  "1",    "Scale Y",       "Transform", 21, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("sz",                      new PropertyDef("sz",                      PropertyType.FLOAT,  "1",    "Scale Z",       "Transform", 22, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry(Model3D.PROP_MODEL_PATH,   new PropertyDef(Model3D.PROP_MODEL_PATH,   PropertyType.STRING, "",     "Model Path",    "Model",     0,  Map.of("asset", "model"))),
                Map.entry(Model3D.PROP_ANIMATION,    new PropertyDef(Model3D.PROP_ANIMATION,    PropertyType.STRING, "",     "Animation",     "Model",     1,  Map.of())),
                Map.entry(Model3D.PROP_ANIMATION_LOOP,  new PropertyDef(Model3D.PROP_ANIMATION_LOOP,  PropertyType.STRING, "loop", "Loop Mode", "Model",  2,  Map.of())),
                Map.entry(Model3D.PROP_ANIMATION_SPEED, new PropertyDef(Model3D.PROP_ANIMATION_SPEED, PropertyType.FLOAT,  "1.0",  "Anim Speed", "Model", 3,  Map.of("min", "0.01", "step", "0.1"))),
                Map.entry("script",                  new PropertyDef("script",                  PropertyType.STRING, null,   "Script",        "Script",    100, Map.of()))
        )));

        registry.registerClass(RootNode.class, "Root");
        registry.registerClass(TickerNode.class, "Ticker");
        registry.registerClass(Model3D.class, "Model3D");
    }
}
