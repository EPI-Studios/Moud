package com.moud.core.builtin;

import com.moud.core.*;
import com.moud.core.scene.PlainNode;

import java.util.Map;

public final class CoreNodeTypesProvider implements NodeTypeProvider {
    @Override
    public void register(NodeTypeRegistry registry) {
        registry.registerType(new NodeTypeDef("Node", "Node", "Core", 0, Map.of(
                "foo", new PropertyDef("foo", PropertyType.STRING, null, "Foo", "Debug", 0, Map.of())
        )));

        registry.registerType(new NodeTypeDef("Node3D", "Node3D", "Core", 10, Map.of(
                "x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1")),
                "y", new PropertyDef("y", PropertyType.FLOAT, "41", "Y", "Transform", 1, Map.of("step", "0.1")),
                "z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1")),
                "rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 10, Map.of("step", "1")),
                "ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 11, Map.of("step", "1")),
                "rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 12, Map.of("step", "1"))
        )));

        registry.registerType(new NodeTypeDef("CSGBlock", "CSG Block", "CSG", 20, Map.of(
                "x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "1")),
                "y", new PropertyDef("y", PropertyType.FLOAT, "41", "Y", "Transform", 1, Map.of("step", "1")),
                "z", new PropertyDef("z", PropertyType.FLOAT, "3", "Z", "Transform", 2, Map.of("step", "1")),
                "rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 3, Map.of("step", "15")),
                "ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 4, Map.of("step", "15")),
                "rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 5, Map.of("step", "15")),
                "sx", new PropertyDef("sx", PropertyType.FLOAT, "1", "Size X", "Size", 10, Map.of("min", "1", "step", "1")),
                "sy", new PropertyDef("sy", PropertyType.FLOAT, "1", "Size Y", "Size", 11, Map.of("min", "1", "step", "1")),
                "sz", new PropertyDef("sz", PropertyType.FLOAT, "1", "Size Z", "Size", 12, Map.of("min", "1", "step", "1")),
                "block", new PropertyDef("block", PropertyType.STRING, "minecraft:stone", "Block", "Render", 20, Map.of())
        )));

        registry.registerClass(PlainNode.class, "Node");
    }
}
