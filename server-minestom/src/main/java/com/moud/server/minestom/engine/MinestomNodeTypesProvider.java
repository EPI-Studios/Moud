package com.moud.server.minestom.engine;


import com.moud.core.NodeTypeDef;
import com.moud.core.PropertyDef;
import com.moud.core.PropertyType;
import java.util.Map;
import com.moud.core.*;
import com.moud.core.scene.Model3D;
import com.moud.server.minestom.engine.anvil.AnvilWorldLoader;
import com.moud.server.minestom.engine.nodes.RootNode;
import com.moud.server.minestom.engine.nodes.TickerNode;

public final class MinestomNodeTypesProvider implements NodeTypeProvider {
    public static final String PROP_SCENE_MODE = "scene_mode";

    @Override
    public int order() {
        return 100;
    }

    @Override
    public void register(NodeTypeRegistry registry) {
        registry.registerType(new NodeTypeDef("Root", "Root", "Minestom", 100, Map.of(
                PROP_SCENE_MODE, new PropertyDef(PROP_SCENE_MODE, PropertyType.STRING, "3d", "Mode", "Scene", 0, Map.of())
        )));
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

        registry.registerType(new NodeTypeDef("PlayerAttachment", "Player Attachment", "Player", 149, Map.ofEntries(
                Map.entry("target",           new PropertyDef("target",           PropertyType.STRING, "all",   "Target",         "Player", 0, Map.of())),
                Map.entry("player_name",      new PropertyDef("player_name",      PropertyType.STRING, "",      "Name",           "Player", 1, Map.of())),
                Map.entry("attachment_point", new PropertyDef("attachment_point", PropertyType.STRING, "root",  "Attach Point",   "Player", 2, Map.of())),
                Map.entry("follow_rotation",  new PropertyDef("follow_rotation",  PropertyType.BOOL,   "false", "Follow Rotation","Player", 3, Map.of())),
                Map.entry("anchor_node_id",   new PropertyDef("anchor_node_id",   PropertyType.STRING, "",      "Anchor Node",    "Player", 10, Map.of())),
                Map.entry("script",           new PropertyDef("script",           PropertyType.STRING, null,    "Script",         "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef(AnvilWorldLoader.TYPE_ID, "Anvil World", "World", 120, Map.of(
                AnvilWorldLoader.PROP_WORLD_PATH, new PropertyDef(AnvilWorldLoader.PROP_WORLD_PATH, PropertyType.STRING, "", "World Path", "World", 0, Map.of())
        )));

        registry.registerClass(RootNode.class, "Root");
        registry.registerClass(TickerNode.class, "Ticker");
        registry.registerClass(Model3D.class, "Model3D");
    }
}
