package com.moud.core.builtin;

import com.moud.core.*;
import com.moud.core.scene.PlainNode;

import java.util.Map;

public final class CoreNodeTypesProvider implements NodeTypeProvider {
    @Override
    public void register(NodeTypeRegistry registry) {
        registry.registerType(new NodeTypeDef("Node", "Node", "Core", 0, Map.of(
                "script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 0, Map.of()),
                "foo", new PropertyDef("foo", PropertyType.STRING, null, "Foo", "Debug", 0, Map.of())
        )));

        registry.registerType(new NodeTypeDef("Node3D", "Node3D", "Core", 10, Map.ofEntries(
                Map.entry("visible", new PropertyDef("visible", PropertyType.BOOL, "true", "Visible", "Editor", -1000, Map.of())),
                Map.entry("editor_locked", new PropertyDef("editor_locked", PropertyType.BOOL, "false", "Locked", "Editor", -999, Map.of())),
                Map.entry("solid", new PropertyDef("solid", PropertyType.BOOL, "true", "Solid", "Collision", 0, Map.of())),
                Map.entry("color_tint_r", new PropertyDef("color_tint_r", PropertyType.FLOAT, "1", "R", "Color Tint", 0, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("color_tint_g", new PropertyDef("color_tint_g", PropertyType.FLOAT, "1", "G", "Color Tint", 1, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("color_tint_b", new PropertyDef("color_tint_b", PropertyType.FLOAT, "1", "B", "Color Tint", 2, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1"))),
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 10, Map.of("step", "1"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 11, Map.of("step", "1"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 12, Map.of("step", "1"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("WorldEnvironment", "WorldEnvironment", "Core", 11, Map.ofEntries(
                Map.entry("fog_enabled", new PropertyDef("fog_enabled", PropertyType.BOOL, "false", "Enabled", "Fog", 0, Map.of())),
                Map.entry("fog_color_r", new PropertyDef("fog_color_r", PropertyType.FLOAT, "0.5", "R", "Fog Color", 1, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("fog_color_g", new PropertyDef("fog_color_g", PropertyType.FLOAT, "0.5", "G", "Fog Color", 2, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("fog_color_b", new PropertyDef("fog_color_b", PropertyType.FLOAT, "0.5", "B", "Fog Color", 3, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("fog_density", new PropertyDef("fog_density", PropertyType.FLOAT, "0.02", "Density", "Fog", 4, Map.of("min", "0", "max", "1", "step", "0.001"))),

                Map.entry("time_enabled", new PropertyDef("time_enabled", PropertyType.BOOL, "true", "Fixed Time", "Time", 10, Map.of())),
                Map.entry("time_ticks", new PropertyDef("time_ticks", PropertyType.INT, "6000", "Time (ticks)", "Time", 11, Map.of("min", "0", "max", "24000", "step", "100"))),
                Map.entry("weather", new PropertyDef("weather", PropertyType.STRING, "clear", "Weather", "Weather", 20, Map.of())),
                Map.entry("ambient_light", new PropertyDef("ambient_light", PropertyType.FLOAT, "1.0", "Ambient", "Light", 30, Map.of("min", "0", "max", "1", "step", "0.05"))),

                Map.entry("sky_mode", new PropertyDef("sky_mode", PropertyType.STRING, "vanilla", "Mode", "Sky", 40, Map.of())),
                Map.entry("sky_shader", new PropertyDef("sky_shader", PropertyType.STRING, "", "Shader", "Sky", 41, Map.of("asset", "shader"))),
                Map.entry("sky_material", new PropertyDef("sky_material", PropertyType.STRING, "", "Material", "Sky", 42, Map.of("asset", "material"))),
                Map.entry("sky_color_top_r", new PropertyDef("sky_color_top_r", PropertyType.FLOAT, "0.2", "Top R", "Sky Colors", 50, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("sky_color_top_g", new PropertyDef("sky_color_top_g", PropertyType.FLOAT, "0.4", "Top G", "Sky Colors", 51, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("sky_color_top_b", new PropertyDef("sky_color_top_b", PropertyType.FLOAT, "0.9", "Top B", "Sky Colors", 52, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("sky_color_horizon_r", new PropertyDef("sky_color_horizon_r", PropertyType.FLOAT, "0.9", "Horizon R", "Sky Colors", 53, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("sky_color_horizon_g", new PropertyDef("sky_color_horizon_g", PropertyType.FLOAT, "0.9", "Horizon G", "Sky Colors", 54, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("sky_color_horizon_b", new PropertyDef("sky_color_horizon_b", PropertyType.FLOAT, "1.0", "Horizon B", "Sky Colors", 55, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("sky_color_sunrise_r", new PropertyDef("sky_color_sunrise_r", PropertyType.FLOAT, "1.0", "Sunrise R", "Sky Colors", 56, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("sky_color_sunrise_g", new PropertyDef("sky_color_sunrise_g", PropertyType.FLOAT, "0.4", "Sunrise G", "Sky Colors", 57, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("sky_color_sunrise_b", new PropertyDef("sky_color_sunrise_b", PropertyType.FLOAT, "0.2", "Sunrise B", "Sky Colors", 58, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("sky_color_sunrise_strength", new PropertyDef("sky_color_sunrise_strength", PropertyType.FLOAT, "1.0", "Sunrise Strength", "Sky Colors", 59, Map.of("min", "0", "max", "1", "step", "0.01"))),

                Map.entry("clouds_mode", new PropertyDef("clouds_mode", PropertyType.STRING, "vanilla", "Mode", "Clouds", 60, Map.of())),
                Map.entry("clouds_shader", new PropertyDef("clouds_shader", PropertyType.STRING, "", "Shader", "Clouds", 61, Map.of("asset", "shader"))),
                Map.entry("clouds_material", new PropertyDef("clouds_material", PropertyType.STRING, "", "Material", "Clouds", 62, Map.of("asset", "material"))),
                Map.entry("cloud_height", new PropertyDef("cloud_height", PropertyType.FLOAT, "128.0", "Height", "Clouds", 70, Map.of("step", "0.5"))),
                Map.entry("cloud_speed", new PropertyDef("cloud_speed", PropertyType.FLOAT, "1.0", "Speed", "Clouds", 71, Map.of("step", "0.05"))),
                Map.entry("cloud_offset_x", new PropertyDef("cloud_offset_x", PropertyType.FLOAT, "0.0", "Offset X", "Clouds", 72, Map.of("step", "0.5"))),
                Map.entry("cloud_offset_z", new PropertyDef("cloud_offset_z", PropertyType.FLOAT, "0.0", "Offset Z", "Clouds", 73, Map.of("step", "0.5"))),
                Map.entry("cloud_scale", new PropertyDef("cloud_scale", PropertyType.FLOAT, "1.0", "Scale", "Clouds", 74, Map.of("min", "0.001", "step", "0.05"))),

                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("Camera3D", "Camera3D", "Core", 12, Map.ofEntries(
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "1.6", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1"))),
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 10, Map.of("step", "1"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 11, Map.of("step", "1"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 12, Map.of("step", "1"))),
                Map.entry("current", new PropertyDef("current", PropertyType.BOOL, "false", "Current", "Camera", 0, Map.of())),
                Map.entry("fov", new PropertyDef("fov", PropertyType.FLOAT, "70", "FOV", "Camera", 20, Map.of("min", "1", "max", "179", "step", "1"))),
                Map.entry("near", new PropertyDef("near", PropertyType.FLOAT, "0.05", "Near", "Camera", 21, Map.of("min", "0.001", "step", "0.01"))),
                Map.entry("far", new PropertyDef("far", PropertyType.FLOAT, "1000", "Far", "Camera", 22, Map.of("min", "1", "step", "1"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("CharacterBody3D", "CharacterBody3D", "Core", 13, Map.of(
                "x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1")),
                "y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.1")),
                "z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1")),
                "ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Yaw", "Transform", 10, Map.of("step", "1")),
                "speed", new PropertyDef("speed", PropertyType.FLOAT, "6", "Speed", "Movement", 20, Map.of("min", "0", "step", "0.1")),
                "script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of())
        )));

        registry.registerType(new NodeTypeDef("SceneInstance3D", "Scene Instance3D", "Scene", 14, Map.ofEntries(
                Map.entry("scene_id", new PropertyDef("scene_id", PropertyType.STRING, "", "Scene Id", "Scene", 0, Map.of())),
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1"))),
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 10, Map.of("step", "1"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 11, Map.of("step", "1"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 12, Map.of("step", "1"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("CSGBlock", "CSG Block", "CSG", 20, Map.ofEntries(
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "1"))),
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 3, Map.of("step", "15"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 4, Map.of("step", "15"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 5, Map.of("step", "15"))),
                Map.entry("sx", new PropertyDef("sx", PropertyType.FLOAT, "1", "Size X", "Size", 10, Map.of("min", "1", "step", "1"))),
                Map.entry("sy", new PropertyDef("sy", PropertyType.FLOAT, "1", "Size Y", "Size", 11, Map.of("min", "1", "step", "1"))),
                Map.entry("sz", new PropertyDef("sz", PropertyType.FLOAT, "1", "Size Z", "Size", 12, Map.of("min", "1", "step", "1"))),
                Map.entry("block", new PropertyDef("block", PropertyType.STRING, "minecraft:stone", "Block", "Render", 20, Map.of())),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("CSGBox", "CSG Box", "CSG", 21, Map.ofEntries(
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "1"))),
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 3, Map.of("step", "15"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 4, Map.of("step", "15"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 5, Map.of("step", "15"))),
                Map.entry("sx", new PropertyDef("sx", PropertyType.FLOAT, "1", "Size X", "Size", 10, Map.of("min", "1", "step", "1"))),
                Map.entry("sy", new PropertyDef("sy", PropertyType.FLOAT, "1", "Size Y", "Size", 11, Map.of("min", "1", "step", "1"))),
                Map.entry("sz", new PropertyDef("sz", PropertyType.FLOAT, "1", "Size Z", "Size", 12, Map.of("min", "1", "step", "1"))),
                Map.entry("texture", new PropertyDef("texture", PropertyType.STRING, "moud:dynamic/white", "Texture", "Material", 20, Map.of("asset", "image"))),
                Map.entry("material", new PropertyDef("material", PropertyType.STRING, "", "Material", "Material", 21, Map.of("asset", "material"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("MeshInstance3D", "MeshInstance3D", "Geometry", 22, Map.ofEntries(
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1"))),
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 10, Map.of("step", "1"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 11, Map.of("step", "1"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 12, Map.of("step", "1"))),
                Map.entry("sx", new PropertyDef("sx", PropertyType.FLOAT, "1", "Scale X", "Transform", 20, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("sy", new PropertyDef("sy", PropertyType.FLOAT, "1", "Scale Y", "Transform", 21, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("sz", new PropertyDef("sz", PropertyType.FLOAT, "1", "Scale Z", "Transform", 22, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("mesh", new PropertyDef("mesh", PropertyType.STRING, "box", "Mesh", "Mesh", 30, Map.of())),
                Map.entry("texture", new PropertyDef("texture", PropertyType.STRING, "moud:dynamic/white", "Texture", "Material", 31, Map.of("asset", "image"))),
                Map.entry("material", new PropertyDef("material", PropertyType.STRING, "", "Material", "Material", 32, Map.of("asset", "material"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("OmniLight3D", "OmniLight3D", "Lighting", 30, Map.ofEntries(
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1"))),
                Map.entry("color_r", new PropertyDef("color_r", PropertyType.FLOAT, "1", "R", "Color", 10, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("color_g", new PropertyDef("color_g", PropertyType.FLOAT, "1", "G", "Color", 11, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("color_b", new PropertyDef("color_b", PropertyType.FLOAT, "1", "B", "Color", 12, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("brightness", new PropertyDef("brightness", PropertyType.FLOAT, "1", "Brightness", "Light", 20, Map.of("min", "0", "step", "0.1"))),
                Map.entry("radius", new PropertyDef("radius", PropertyType.FLOAT, "8", "Radius", "Light", 21, Map.of("min", "0", "step", "0.1"))),
                Map.entry("enabled", new PropertyDef("enabled", PropertyType.BOOL, "true", "Enabled", "Light", 22, Map.of())),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("DirectionalLight3D", "DirectionalLight3D", "Lighting", 31, Map.ofEntries(
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 10, Map.of("step", "1"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 11, Map.of("step", "1"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 12, Map.of("step", "1"))),
                Map.entry("color_r", new PropertyDef("color_r", PropertyType.FLOAT, "1", "R", "Color", 10, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("color_g", new PropertyDef("color_g", PropertyType.FLOAT, "1", "G", "Color", 11, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("color_b", new PropertyDef("color_b", PropertyType.FLOAT, "1", "B", "Color", 12, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("brightness", new PropertyDef("brightness", PropertyType.FLOAT, "1", "Brightness", "Light", 20, Map.of("min", "0", "step", "0.1"))),
                Map.entry("enabled", new PropertyDef("enabled", PropertyType.BOOL, "true", "Enabled", "Light", 21, Map.of())),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("SpotLight3D", "SpotLight3D", "Lighting", 32, Map.ofEntries(
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1"))),
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 10, Map.of("step", "1"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 11, Map.of("step", "1"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 12, Map.of("step", "1"))),
                Map.entry("color_r", new PropertyDef("color_r", PropertyType.FLOAT, "1", "R", "Color", 10, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("color_g", new PropertyDef("color_g", PropertyType.FLOAT, "1", "G", "Color", 11, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("color_b", new PropertyDef("color_b", PropertyType.FLOAT, "1", "B", "Color", 12, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("brightness", new PropertyDef("brightness", PropertyType.FLOAT, "1", "Brightness", "Light", 20, Map.of("min", "0", "step", "0.1"))),
                Map.entry("angle", new PropertyDef("angle", PropertyType.FLOAT, "45", "Angle", "Light", 21, Map.of("min", "0", "max", "180", "step", "1"))),
                Map.entry("distance", new PropertyDef("distance", PropertyType.FLOAT, "10", "Distance", "Light", 22, Map.of("min", "0", "step", "0.1"))),
                Map.entry("enabled", new PropertyDef("enabled", PropertyType.BOOL, "true", "Enabled", "Light", 23, Map.of())),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerClass(PlainNode.class, "Node");
    }
}
