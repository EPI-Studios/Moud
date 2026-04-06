package com.moud.core.builtin;

import com.moud.core.*;
import com.moud.core.NodeTypeDef;
import com.moud.core.PropertyDef;
import com.moud.core.PropertyType;
import com.moud.core.math.Transform;
import com.moud.core.scene.Node;
import com.moud.core.scene.PlainNode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CoreNodeTypesProvider implements NodeTypeProvider {
    @Override
    public void register(NodeTypeRegistry registry) {
        registry.registerType(new NodeTypeDef("Node", "Node", "Core", 0, Map.of(
                "script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 0, Map.of()),
                "foo", new PropertyDef("foo", PropertyType.STRING, null, "Foo", "Debug", 0, Map.of())
        )));

        registry.registerType(new NodeTypeDef("Node3D", "Node", "Node3D", "Core", 10, Map.ofEntries(
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

        registry.registerType(new NodeTypeDef("Node2D", "Node", "Node2D", "2D", 11, Map.ofEntries(
                Map.entry("visible", new PropertyDef("visible", PropertyType.BOOL, "true", "Visible", "Editor", -1000, Map.of())),
                Map.entry("editor_locked", new PropertyDef("editor_locked", PropertyType.BOOL, "false", "Locked", "Editor", -999, Map.of())),
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("sx", new PropertyDef("sx", PropertyType.FLOAT, "1", "Scale X", "Transform", 2, Map.of("min", "0.01", "step", "0.05"))),
                Map.entry("sy", new PropertyDef("sy", PropertyType.FLOAT, "1", "Scale Y", "Transform", 3, Map.of("min", "0.01", "step", "0.05"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rotation", "Transform", 10, Map.of("step", "1"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("WorldEnvironment", "Node", "WorldEnvironment", "Core", 11, Map.ofEntries(
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

        registry.registerType(new NodeTypeDef("Camera3D", "Node3D", "Camera3D", "Core", 12, Map.ofEntries(
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

        registry.registerType(new NodeTypeDef("PlayerStart", "Node3D", "Player Start", "Core", 13, Map.ofEntries(
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "64", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Facing (Yaw)", "Transform", 10, Map.of("step", "1"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("SceneInstance3D", "Node3D", "Scene Instance3D", "Scene", 14, Map.ofEntries(
                Map.entry("scene_id", new PropertyDef("scene_id", PropertyType.STRING, "", "Scene Id", "Scene", 0, Map.of())),
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1"))),
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 10, Map.of("step", "1"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 11, Map.of("step", "1"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 12, Map.of("step", "1"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("CSGBlock", "Node3D", "CSG Block", "CSG", 20, Map.ofEntries(
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
                Map.entry("solid", new PropertyDef("solid", PropertyType.BOOL, "true", "Collision", "Physics", 30, Map.of())),
                Map.entry("collision_layer", new PropertyDef("collision_layer", PropertyType.INT, "1", "Layer", "Physics", 31, Map.of("min", "0", "step", "1"))),
                Map.entry("collision_mask", new PropertyDef("collision_mask", PropertyType.INT, "1", "Mask", "Physics", 32, Map.of("min", "0", "step", "1"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("CSGBox", "Node3D", "CSG Box", "CSG", 21, Map.ofEntries(
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
                Map.entry("mesh", new PropertyDef("mesh", PropertyType.STRING, "cube", "Mesh", "Material", 21, Map.of())),
                Map.entry("opacity", new PropertyDef("opacity", PropertyType.FLOAT, "1", "Opacity", "Material", 22, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("uv_scale_x", new PropertyDef("uv_scale_x", PropertyType.FLOAT, "1", "UV Scale X", "Material", 23, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("uv_scale_y", new PropertyDef("uv_scale_y", PropertyType.FLOAT, "1", "UV Scale Y", "Material", 24, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("uv_offset_x", new PropertyDef("uv_offset_x", PropertyType.FLOAT, "0", "UV Offset X", "Material", 25, Map.of("step", "0.05"))),
                Map.entry("uv_offset_y", new PropertyDef("uv_offset_y", PropertyType.FLOAT, "0", "UV Offset Y", "Material", 26, Map.of("step", "0.05"))),
                Map.entry("color_tint_r", new PropertyDef("color_tint_r", PropertyType.FLOAT, "1", "R", "Color Tint", 0, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("color_tint_g", new PropertyDef("color_tint_g", PropertyType.FLOAT, "1", "G", "Color Tint", 1, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("color_tint_b", new PropertyDef("color_tint_b", PropertyType.FLOAT, "1", "B", "Color Tint", 2, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("solid", new PropertyDef("solid", PropertyType.BOOL, "true", "Collision", "Physics", 30, Map.of())),
                Map.entry("collision_layer", new PropertyDef("collision_layer", PropertyType.INT, "1", "Layer", "Physics", 31, Map.of("min", "0", "step", "1"))),
                Map.entry("collision_mask", new PropertyDef("collision_mask", PropertyType.INT, "1", "Mask", "Physics", 32, Map.of("min", "0", "step", "1"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("MeshInstance3D", "Node3D", "MeshInstance3D", "Geometry", 22, Map.ofEntries(
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
                Map.entry("billboard", new PropertyDef("billboard", PropertyType.BOOL, "false", "Billboard", "Mesh", 31, Map.of())),
                Map.entry("double_sided", new PropertyDef("double_sided", PropertyType.BOOL, "false", "Double Sided", "Mesh", 32, Map.of())),
                Map.entry("texture", new PropertyDef("texture", PropertyType.STRING, "moud:dynamic/white", "Texture", "Material", 33, Map.of("asset", "image"))),
                Map.entry("material", new PropertyDef("material", PropertyType.STRING, "", "Material", "Material", 33, Map.of("asset", "material"))),
                Map.entry("opacity", new PropertyDef("opacity", PropertyType.FLOAT, "1", "Opacity", "Material", 34, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("uv_scale_x", new PropertyDef("uv_scale_x", PropertyType.FLOAT, "1", "UV Scale X", "Material", 40, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("uv_scale_y", new PropertyDef("uv_scale_y", PropertyType.FLOAT, "1", "UV Scale Y", "Material", 41, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("uv_offset_x", new PropertyDef("uv_offset_x", PropertyType.FLOAT, "0", "UV Offset X", "Material", 42, Map.of("step", "0.05"))),
                Map.entry("uv_offset_y", new PropertyDef("uv_offset_y", PropertyType.FLOAT, "0", "UV Offset Y", "Material", 43, Map.of("step", "0.05"))),
                Map.entry("color_tint_r", new PropertyDef("color_tint_r", PropertyType.FLOAT, "1", "R", "Color Tint", 0, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("color_tint_g", new PropertyDef("color_tint_g", PropertyType.FLOAT, "1", "G", "Color Tint", 1, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("color_tint_b", new PropertyDef("color_tint_b", PropertyType.FLOAT, "1", "B", "Color Tint", 2, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("MultiMeshInstance3D", "Node3D", "MultiMeshInstance3D", "Geometry", 23, Map.ofEntries(
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1"))),
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 10, Map.of("step", "1"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 11, Map.of("step", "1"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 12, Map.of("step", "1"))),
                Map.entry("mesh", new PropertyDef("mesh", PropertyType.STRING, "box", "Mesh", "Mesh", 30, Map.of())),
                Map.entry("material", new PropertyDef("material", PropertyType.STRING, "", "Material", "Material", 32, Map.of("asset", "material"))),
                Map.entry("instance_count", new PropertyDef("instance_count", PropertyType.INT, "0", "Instance Count", "Mesh", 33, Map.of("min", "0", "step", "1"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("Sprite3D", "Node3D", "Sprite3D", "Geometry", 24, Map.ofEntries(
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1"))),
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 10, Map.of("step", "1"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 11, Map.of("step", "1"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 12, Map.of("step", "1"))),
                Map.entry("sx", new PropertyDef("sx", PropertyType.FLOAT, "1", "Width", "Transform", 20, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("sy", new PropertyDef("sy", PropertyType.FLOAT, "1", "Height", "Transform", 21, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("sz", new PropertyDef("sz", PropertyType.FLOAT, "1", "Depth", "Transform", 22, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("mesh", new PropertyDef("mesh", PropertyType.STRING, "plane", "Mesh", "Mesh", 25, Map.of())),
                Map.entry("billboard", new PropertyDef("billboard", PropertyType.BOOL, "true", "Billboard", "Mesh", 26, Map.of())),
                Map.entry("double_sided", new PropertyDef("double_sided", PropertyType.BOOL, "true", "Double Sided", "Mesh", 27, Map.of())),
                Map.entry("texture", new PropertyDef("texture", PropertyType.STRING, "moud:dynamic/white", "Texture", "Material", 30, Map.of("asset", "image"))),
                Map.entry("material", new PropertyDef("material", PropertyType.STRING, "", "Material", "Material", 31, Map.of("asset", "material"))),
                Map.entry("opacity", new PropertyDef("opacity", PropertyType.FLOAT, "1", "Opacity", "Material", 32, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("uv_scale_x", new PropertyDef("uv_scale_x", PropertyType.FLOAT, "1", "UV Scale X", "Material", 40, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("uv_scale_y", new PropertyDef("uv_scale_y", PropertyType.FLOAT, "1", "UV Scale Y", "Material", 41, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("uv_offset_x", new PropertyDef("uv_offset_x", PropertyType.FLOAT, "0", "UV Offset X", "Material", 42, Map.of("step", "0.05"))),
                Map.entry("uv_offset_y", new PropertyDef("uv_offset_y", PropertyType.FLOAT, "0", "UV Offset Y", "Material", 43, Map.of("step", "0.05"))),
                Map.entry("color_tint_r", new PropertyDef("color_tint_r", PropertyType.FLOAT, "1", "R", "Color Tint", 0, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("color_tint_g", new PropertyDef("color_tint_g", PropertyType.FLOAT, "1", "G", "Color Tint", 1, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("color_tint_b", new PropertyDef("color_tint_b", PropertyType.FLOAT, "1", "B", "Color Tint", 2, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("Decal", "Node3D", "Decal", "Geometry", 25, Map.ofEntries(
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1"))),
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 10, Map.of("step", "1"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 11, Map.of("step", "1"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 12, Map.of("step", "1"))),
                Map.entry("sx", new PropertyDef("sx", PropertyType.FLOAT, "1", "Size X", "Projection", 20, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("sy", new PropertyDef("sy", PropertyType.FLOAT, "1", "Size Y", "Projection", 21, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("sz", new PropertyDef("sz", PropertyType.FLOAT, "1", "Depth", "Projection", 22, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("texture", new PropertyDef("texture", PropertyType.STRING, "moud:dynamic/white", "Texture", "Material", 30, Map.of("asset", "image"))),
                Map.entry("material", new PropertyDef("material", PropertyType.STRING, "", "Material", "Material", 31, Map.of("asset", "material"))),
                Map.entry("opacity", new PropertyDef("opacity", PropertyType.FLOAT, "1", "Opacity", "Material", 32, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("PlayerAttachment", "Node3D", "Player Attachment", "Player", 25, Map.ofEntries(
                Map.entry("target", new PropertyDef("target", PropertyType.STRING, "all", "Target", "Player", 0, Map.of())),
                Map.entry("player_name", new PropertyDef("player_name", PropertyType.STRING, "", "Name", "Player", 1, Map.of())),
                Map.entry("attachment_point", new PropertyDef("attachment_point", PropertyType.STRING, "root", "Attach Point", "Player", 2, Map.of())),
                Map.entry("follow_rotation", new PropertyDef("follow_rotation", PropertyType.BOOL, "false", "Follow Rotation", "Player", 3, Map.of())),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("AudioPlayer2D", "Node", "Audio Player 2D", "Audio", 27, Map.ofEntries(
                Map.entry("sound_id", new PropertyDef("sound_id", PropertyType.STRING, "", "Sound Id", "Audio", 0, Map.of("asset", "audio"))),
                Map.entry("playing", new PropertyDef("playing", PropertyType.BOOL, "true", "Playing", "Playback", 10, Map.of())),
                Map.entry("loop", new PropertyDef("loop", PropertyType.BOOL, "true", "Loop", "Playback", 11, Map.of())),
                Map.entry("volume_db", new PropertyDef("volume_db", PropertyType.FLOAT, "0", "Volume dB", "Playback", 12, Map.of("step", "0.5"))),
                Map.entry("pitch_scale", new PropertyDef("pitch_scale", PropertyType.FLOAT, "1", "Pitch", "Playback", 13, Map.of("min", "0.01", "step", "0.01"))),
                Map.entry("category", new PropertyDef("category", PropertyType.STRING, "master", "Category", "Playback", 14, Map.of())),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("AudioPlayer3D", "Node3D", "Audio Player 3D", "Audio", 28, Map.ofEntries(
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1"))),
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 10, Map.of("step", "1"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 11, Map.of("step", "1"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 12, Map.of("step", "1"))),
                Map.entry("sx", new PropertyDef("sx", PropertyType.FLOAT, "1", "Scale X", "Transform", 20, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("sy", new PropertyDef("sy", PropertyType.FLOAT, "1", "Scale Y", "Transform", 21, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("sz", new PropertyDef("sz", PropertyType.FLOAT, "1", "Scale Z", "Transform", 22, Map.of("min", "0.001", "step", "0.1"))),
                Map.entry("sound_id", new PropertyDef("sound_id", PropertyType.STRING, "", "Sound Id", "Audio", 30, Map.of("asset", "audio"))),
                Map.entry("playing", new PropertyDef("playing", PropertyType.BOOL, "true", "Playing", "Playback", 31, Map.of())),
                Map.entry("loop", new PropertyDef("loop", PropertyType.BOOL, "true", "Loop", "Playback", 32, Map.of())),
                Map.entry("volume_db", new PropertyDef("volume_db", PropertyType.FLOAT, "0", "Volume dB", "Playback", 33, Map.of("step", "0.5"))),
                Map.entry("pitch_scale", new PropertyDef("pitch_scale", PropertyType.FLOAT, "1", "Pitch", "Playback", 34, Map.of("min", "0.01", "step", "0.01"))),
                Map.entry("category", new PropertyDef("category", PropertyType.STRING, "ambient", "Category", "Playback", 35, Map.of())),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("OmniLight3D", "Node3D", "OmniLight3D", "Lighting", 30, Map.ofEntries(
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

        registry.registerType(new NodeTypeDef("DirectionalLight3D", "Node3D", "DirectionalLight3D", "Lighting", 31, Map.ofEntries(
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

        registry.registerType(new NodeTypeDef("SpotLight3D", "Node3D", "SpotLight3D", "Lighting", 32, Map.ofEntries(
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

        registry.registerType(new NodeTypeDef("StaticBody3D", "Node3D", "StaticBody3D", "Physics", 40, Map.ofEntries(
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1"))),
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 10, Map.of("step", "1"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 11, Map.of("step", "1"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 12, Map.of("step", "1"))),
                Map.entry("shape", new PropertyDef("shape", PropertyType.STRING, "box", "Shape", "Collision", 0, Map.of())),
                Map.entry("sx", new PropertyDef("sx", PropertyType.FLOAT, "1", "Size X", "Collision", 1, Map.of("min", "0.01", "step", "0.1"))),
                Map.entry("sy", new PropertyDef("sy", PropertyType.FLOAT, "1", "Size Y", "Collision", 2, Map.of("min", "0.01", "step", "0.1"))),
                Map.entry("sz", new PropertyDef("sz", PropertyType.FLOAT, "1", "Size Z", "Collision", 3, Map.of("min", "0.01", "step", "0.1"))),
                Map.entry("radius", new PropertyDef("radius", PropertyType.FLOAT, "0.5", "Radius", "Collision", 4, Map.of("min", "0.01", "step", "0.05"))),
                Map.entry("enabled", new PropertyDef("enabled", PropertyType.BOOL, "true", "Enabled", "Collision", 10, Map.of())),
                Map.entry("collision_layer", new PropertyDef("collision_layer", PropertyType.INT, "1", "Layer", "Collision", 11, Map.of("min", "0", "step", "1"))),
                Map.entry("collision_mask", new PropertyDef("collision_mask", PropertyType.INT, "1", "Mask", "Collision", 12, Map.of("min", "0", "step", "1"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("RigidBody3D", "Node3D", "RigidBody3D", "Physics", 41, Map.ofEntries(
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1"))),
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 10, Map.of("step", "1"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 11, Map.of("step", "1"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 12, Map.of("step", "1"))),
                Map.entry("shape", new PropertyDef("shape", PropertyType.STRING, "box", "Shape", "Collision", 0, Map.of())),
                Map.entry("sx", new PropertyDef("sx", PropertyType.FLOAT, "1", "Size X", "Collision", 1, Map.of("min", "0.01", "step", "0.1"))),
                Map.entry("sy", new PropertyDef("sy", PropertyType.FLOAT, "1", "Size Y", "Collision", 2, Map.of("min", "0.01", "step", "0.1"))),
                Map.entry("sz", new PropertyDef("sz", PropertyType.FLOAT, "1", "Size Z", "Collision", 3, Map.of("min", "0.01", "step", "0.1"))),
                Map.entry("radius", new PropertyDef("radius", PropertyType.FLOAT, "0.5", "Radius", "Collision", 4, Map.of("min", "0.01", "step", "0.05"))),
                Map.entry("mass", new PropertyDef("mass", PropertyType.FLOAT, "1", "Mass", "Physics", 0, Map.of("min", "0.01", "step", "0.1"))),
                Map.entry("gravity_scale", new PropertyDef("gravity_scale", PropertyType.FLOAT, "1", "Gravity Scale", "Physics", 1, Map.of("step", "0.1"))),
                Map.entry("linear_damping", new PropertyDef("linear_damping", PropertyType.FLOAT, "0.1", "Linear Damping", "Physics", 2, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("angular_damping", new PropertyDef("angular_damping", PropertyType.FLOAT, "0.1", "Angular Damping", "Physics", 3, Map.of("min", "0", "max", "1", "step", "0.01"))),
                Map.entry("freeze", new PropertyDef("freeze", PropertyType.BOOL, "false", "Freeze", "Physics", 10, Map.of())),
                Map.entry("enabled", new PropertyDef("enabled", PropertyType.BOOL, "true", "Enabled", "Collision", 10, Map.of())),
                Map.entry("collision_layer", new PropertyDef("collision_layer", PropertyType.INT, "1", "Layer", "Collision", 11, Map.of("min", "0", "step", "1"))),
                Map.entry("collision_mask", new PropertyDef("collision_mask", PropertyType.INT, "1", "Mask", "Collision", 12, Map.of("min", "0", "step", "1"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("CharacterBody3D", "Node3D", "CharacterBody3D", "Physics", 42, Map.ofEntries(
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1"))),
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 10, Map.of("step", "1"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 11, Map.of("step", "1"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 12, Map.of("step", "1"))),
                Map.entry("shape", new PropertyDef("shape", PropertyType.STRING, "capsule", "Shape", "Collision", 0, Map.of())),
                Map.entry("radius", new PropertyDef("radius", PropertyType.FLOAT, "0.3", "Radius", "Collision", 1, Map.of("min", "0.01", "step", "0.05"))),
                Map.entry("height", new PropertyDef("height", PropertyType.FLOAT, "1.8", "Height", "Collision", 2, Map.of("min", "0.01", "step", "0.1"))),
                Map.entry("collision_layer", new PropertyDef("collision_layer", PropertyType.INT, "1", "Layer", "Collision", 11, Map.of("min", "0", "step", "1"))),
                Map.entry("collision_mask", new PropertyDef("collision_mask", PropertyType.INT, "1", "Mask", "Collision", 12, Map.of("min", "0", "step", "1"))),
                Map.entry("speed", new PropertyDef("speed", PropertyType.FLOAT, "5", "Speed", "Movement", 0, Map.of("min", "0", "step", "0.5"))),
                Map.entry("jump_velocity", new PropertyDef("jump_velocity", PropertyType.FLOAT, "10", "Jump Velocity", "Movement", 1, Map.of("min", "0", "step", "0.5"))),
                Map.entry("gravity_scale", new PropertyDef("gravity_scale", PropertyType.FLOAT, "1", "Gravity Scale", "Movement", 2, Map.of("step", "0.1"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("Area3D", "Node3D", "Area3D", "Physics", 43, Map.ofEntries(
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1"))),
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 10, Map.of("step", "1"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 11, Map.of("step", "1"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 12, Map.of("step", "1"))),
                Map.entry("shape", new PropertyDef("shape", PropertyType.STRING, "box", "Shape", "Area", 0, Map.of())),
                Map.entry("sx", new PropertyDef("sx", PropertyType.FLOAT, "1", "Size X", "Area", 1, Map.of("min", "0.01", "step", "0.1"))),
                Map.entry("sy", new PropertyDef("sy", PropertyType.FLOAT, "1", "Size Y", "Area", 2, Map.of("min", "0.01", "step", "0.1"))),
                Map.entry("sz", new PropertyDef("sz", PropertyType.FLOAT, "1", "Size Z", "Area", 3, Map.of("min", "0.01", "step", "0.1"))),
                Map.entry("radius", new PropertyDef("radius", PropertyType.FLOAT, "1", "Radius", "Area", 4, Map.of("min", "0.01", "step", "0.1"))),
                Map.entry("monitoring", new PropertyDef("monitoring", PropertyType.BOOL, "true", "Monitoring", "Area", 10, Map.of())),
                Map.entry("collision_layer", new PropertyDef("collision_layer", PropertyType.INT, "1", "Layer", "Area", 11, Map.of("min", "0", "step", "1"))),
                Map.entry("collision_mask", new PropertyDef("collision_mask", PropertyType.INT, "1", "Mask", "Area", 12, Map.of("min", "0", "step", "1"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("Raycast3D", "Node3D", "Raycast3D", "Physics", 44, Map.ofEntries(
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1"))),
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 10, Map.of("step", "1"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 11, Map.of("step", "1"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 12, Map.of("step", "1"))),
                Map.entry("target_x", new PropertyDef("target_x", PropertyType.FLOAT, "0", "Target X", "Ray", 0, Map.of("step", "0.1"))),
                Map.entry("target_y", new PropertyDef("target_y", PropertyType.FLOAT, "-1", "Target Y", "Ray", 1, Map.of("step", "0.1"))),
                Map.entry("target_z", new PropertyDef("target_z", PropertyType.FLOAT, "0", "Target Z", "Ray", 2, Map.of("step", "0.1"))),
                Map.entry("max_distance", new PropertyDef("max_distance", PropertyType.FLOAT, "100", "Max Distance", "Ray", 3, Map.of("min", "0", "step", "1"))),
                Map.entry("enabled", new PropertyDef("enabled", PropertyType.BOOL, "true", "Enabled", "Ray", 10, Map.of())),
                Map.entry("collision_layer", new PropertyDef("collision_layer", PropertyType.INT, "1", "Layer", "Ray", 11, Map.of("min", "0", "step", "1"))),
                Map.entry("collision_mask", new PropertyDef("collision_mask", PropertyType.INT, "1", "Mask", "Ray", 12, Map.of("min", "0", "step", "1"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("Marker3D", "Node3D", "Marker3D", "Markers", 50, Map.ofEntries(
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.1"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.1"))),
                Map.entry("z", new PropertyDef("z", PropertyType.FLOAT, "0", "Z", "Transform", 2, Map.of("step", "0.1"))),
                Map.entry("rx", new PropertyDef("rx", PropertyType.FLOAT, "0", "Rot X", "Transform", 10, Map.of("step", "1"))),
                Map.entry("ry", new PropertyDef("ry", PropertyType.FLOAT, "0", "Rot Y", "Transform", 11, Map.of("step", "1"))),
                Map.entry("rz", new PropertyDef("rz", PropertyType.FLOAT, "0", "Rot Z", "Transform", 12, Map.of("step", "1"))),
                Map.entry("gizmo_size", new PropertyDef("gizmo_size", PropertyType.FLOAT, "0.5", "Gizmo Size", "Display", 0, Map.of("min", "0.1", "step", "0.1"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("CanvasItem", "CanvasLayer", "Canvas Item", "UI", 55,
                new B(200, 200)
                        .add("z_index", PropertyType.INT, "0", "Z Index", "Render", 0, Map.of("step", "1"))
                        .build()));

        registry.registerType(new NodeTypeDef("Control", "CanvasItem", "Control", "UI", 56, new B(200, 100).build()));

        registry.registerType(new NodeTypeDef("CanvasLayer", "Node", "Canvas Layer", "UI", 57, Map.ofEntries(
                Map.entry("visible", new PropertyDef("visible", PropertyType.BOOL, "true", "Visible", "Editor", -1000, Map.of())),
                Map.entry("editor_locked", new PropertyDef("editor_locked", PropertyType.BOOL, "false", "Locked", "Editor", -999, Map.of())),
                Map.entry("x", new PropertyDef("x", PropertyType.FLOAT, "0", "X", "Transform", 0, Map.of("step", "0.5"))),
                Map.entry("y", new PropertyDef("y", PropertyType.FLOAT, "0", "Y", "Transform", 1, Map.of("step", "0.5"))),
                Map.entry("layer", new PropertyDef("layer", PropertyType.INT, "1", "Layer", "Layer", 0, Map.of("step", "1"))),
                Map.entry("script", new PropertyDef("script", PropertyType.STRING, null, "Script", "Script", 100, Map.of()))
        )));

        registry.registerType(new NodeTypeDef("HBoxContainer", "Control", "HBox Container", "UI", 60,
                new B(200, 50)
                        .add("separation", PropertyType.INT, "4", "Separation", "Layout", 0, Map.of("min", "0", "step", "1"))
                        .build()));

        registry.registerType(new NodeTypeDef("VBoxContainer", "Control", "VBox Container", "UI", 61,
                new B(120, 200)
                        .add("separation", PropertyType.INT, "4", "Separation", "Layout", 0, Map.of("min", "0", "step", "1"))
                        .build()));

        registry.registerType(new NodeTypeDef("GridContainer", "Control", "Grid Container", "UI", 62,
                new B(200, 200)
                        .add("columns", PropertyType.INT, "2", "Columns", "Layout", 0, Map.of("min", "1", "step", "1"))
                        .add("h_separation", PropertyType.INT, "4", "H Separation", "Layout", 1, Map.of("min", "0", "step", "1"))
                        .add("v_separation", PropertyType.INT, "4", "V Separation", "Layout", 2, Map.of("min", "0", "step", "1"))
                        .build()));

        registry.registerType(new NodeTypeDef("MarginContainer", "Control", "Margin Container", "UI", 63,
                new B(200, 200)
                        .add("margin_content_left", PropertyType.INT, "8", "Left", "Content Margin", 0, Map.of("min", "0", "step", "1"))
                        .add("margin_content_right", PropertyType.INT, "8", "Right", "Content Margin", 1, Map.of("min", "0", "step", "1"))
                        .add("margin_content_top", PropertyType.INT, "8", "Top", "Content Margin", 2, Map.of("min", "0", "step", "1"))
                        .add("margin_content_bottom", PropertyType.INT, "8", "Bottom", "Content Margin", 3, Map.of("min", "0", "step", "1"))
                        .build()));

        registry.registerType(new NodeTypeDef("ScrollContainer", "Control", "Scroll Container", "UI", 64,
                new B(200, 200)
                        .add("h_scroll_enabled", PropertyType.BOOL, "true", "H Scroll", "Scroll", 0, Map.of())
                        .add("v_scroll_enabled", PropertyType.BOOL, "true", "V Scroll", "Scroll", 1, Map.of())
                        .add("scroll_horizontal", PropertyType.INT, "0", "Scroll H", "Scroll", 2, Map.of("step", "1"))
                        .add("scroll_vertical", PropertyType.INT, "0", "Scroll V", "Scroll", 3, Map.of("step", "1"))
                        .build()));

        registry.registerType(new NodeTypeDef("PanelContainer", "Control", "Panel Container", "UI", 65, new B(200, 200).build()));

        registry.registerType(new NodeTypeDef("Label", "Control", "Label", "UI", 70,
                new B(120, 24)
                        .add("text", PropertyType.STRING, "Label", "Text", "Content", 0, Map.of())
                        .add("font_size", PropertyType.INT, "16", "Font Size", "Content", 1, Map.of("min", "4", "max", "128", "step", "1"))
                        .add("h_align", PropertyType.STRING, "left", "H Align", "Content", 2, Map.of())
                        .add("v_align", PropertyType.STRING, "center", "V Align", "Content", 3, Map.of())
                        .add("autowrap", PropertyType.BOOL, "false", "Autowrap", "Content", 4, Map.of())
                        .add("color_r", PropertyType.FLOAT, "1", "R", "Color", 0, Map.of("min", "0", "max", "1", "step", "0.01"))
                        .add("color_g", PropertyType.FLOAT, "1", "G", "Color", 1, Map.of("min", "0", "max", "1", "step", "0.01"))
                        .add("color_b", PropertyType.FLOAT, "1", "B", "Color", 2, Map.of("min", "0", "max", "1", "step", "0.01"))
                        .add("color_a", PropertyType.FLOAT, "1", "A", "Color", 3, Map.of("min", "0", "max", "1", "step", "0.01"))
                        .build()));

        registry.registerType(new NodeTypeDef("RichTextLabel", "Control", "Rich Text Label", "UI", 71,
                new B(200, 80)
                        .add("text", PropertyType.STRING, "", "Text", "Content", 0, Map.of())
                        .add("bbcode_enabled", PropertyType.BOOL, "true", "BBCode", "Content", 1, Map.of())
                        .add("fit_content", PropertyType.BOOL, "false", "Fit Content", "Content", 2, Map.of())
                        .build()));

        registry.registerType(new NodeTypeDef("TextureRect", "Control", "Texture Rect", "UI", 72,
                new B(128, 128)
                        .add("texture", PropertyType.STRING, "", "Texture", "Content", 0, Map.of("asset", "image"))
                        .add("stretch_mode", PropertyType.STRING, "scale", "Stretch Mode", "Content", 1, Map.of())
                        .add("flip_h", PropertyType.BOOL, "false", "Flip H", "Content", 2, Map.of())
                        .add("flip_v", PropertyType.BOOL, "false", "Flip V", "Content", 3, Map.of())
                        .build()));

        registry.registerType(new NodeTypeDef("ColorRect", "Control", "Color Rect", "UI", 73,
                new B(100, 100)
                        .add("color_r", PropertyType.FLOAT, "1", "R", "Color", 0, Map.of("min", "0", "max", "1", "step", "0.01"))
                        .add("color_g", PropertyType.FLOAT, "0", "G", "Color", 1, Map.of("min", "0", "max", "1", "step", "0.01"))
                        .add("color_b", PropertyType.FLOAT, "0", "B", "Color", 2, Map.of("min", "0", "max", "1", "step", "0.01"))
                        .add("color_a", PropertyType.FLOAT, "1", "A", "Color", 3, Map.of("min", "0", "max", "1", "step", "0.01"))
                        .build()));

        registry.registerType(new NodeTypeDef("ProgressBar", "Control", "Progress Bar", "UI", 74,
                new B(200, 24)
                        .add("value", PropertyType.FLOAT, "50", "Value", "Progress", 0, Map.of("step", "1"))
                        .add("min_value", PropertyType.FLOAT, "0", "Min", "Progress", 1, Map.of("step", "1"))
                        .add("max_value", PropertyType.FLOAT, "100", "Max", "Progress", 2, Map.of("step", "1"))
                        .add("show_percentage", PropertyType.BOOL, "true", "Show %", "Progress", 3, Map.of())
                        .add("fill_color_r", PropertyType.FLOAT, "0.2", "R", "Fill Color", 0, Map.of("min", "0", "max", "1", "step", "0.01"))
                        .add("fill_color_g", PropertyType.FLOAT, "0.7", "G", "Fill Color", 1, Map.of("min", "0", "max", "1", "step", "0.01"))
                        .add("fill_color_b", PropertyType.FLOAT, "0.3", "B", "Fill Color", 2, Map.of("min", "0", "max", "1", "step", "0.01"))
                        .add("fill_color_a", PropertyType.FLOAT, "1", "A", "Fill Color", 3, Map.of("min", "0", "max", "1", "step", "0.01"))
                        .build()));

        registry.registerType(new NodeTypeDef("Button", "Control", "Button", "UI", 80,
                new B(120, 36)
                        .add("text", PropertyType.STRING, "Button", "Text", "Content", 0, Map.of())
                        .add("icon", PropertyType.STRING, "", "Icon", "Content", 1, Map.of("asset", "image"))
                        .add("disabled", PropertyType.BOOL, "false", "Disabled", "State", 0, Map.of())
                        .add("toggle_mode", PropertyType.BOOL, "false", "Toggle Mode", "State", 1, Map.of())
                        .add("pressed", PropertyType.BOOL, "false", "Pressed", "State", 2, Map.of())
                        .build()));

        registry.registerType(new NodeTypeDef("TextureButton", "Control", "Texture Button", "UI", 81,
                new B(64, 64)
                        .add("texture_normal", PropertyType.STRING, "", "Normal", "Textures", 0, Map.of("asset", "image"))
                        .add("texture_pressed", PropertyType.STRING, "", "Pressed", "Textures", 1, Map.of("asset", "image"))
                        .add("texture_hover", PropertyType.STRING, "", "Hover", "Textures", 2, Map.of("asset", "image"))
                        .add("texture_disabled", PropertyType.STRING, "", "Disabled", "Textures", 3, Map.of("asset", "image"))
                        .add("disabled", PropertyType.BOOL, "false", "Disabled", "State", 0, Map.of())
                        .build()));

        registry.registerType(new NodeTypeDef("CheckBox", "Control", "Check Box", "UI", 82,
                new B(120, 28)
                        .add("text", PropertyType.STRING, "Check Box", "Text", "Content", 0, Map.of())
                        .add("checked", PropertyType.BOOL, "false", "Checked", "State", 0, Map.of())
                        .add("disabled", PropertyType.BOOL, "false", "Disabled", "State", 1, Map.of())
                        .build()));

        registry.registerType(new NodeTypeDef("HSlider", "Control", "H Slider", "UI", 83,
                new B(200, 24)
                        .add("value", PropertyType.FLOAT, "0", "Value", "Range", 0, Map.of("step", "0.01"))
                        .add("min_value", PropertyType.FLOAT, "0", "Min", "Range", 1, Map.of("step", "1"))
                        .add("max_value", PropertyType.FLOAT, "100", "Max", "Range", 2, Map.of("step", "1"))
                        .add("step", PropertyType.FLOAT, "1", "Step", "Range", 3, Map.of("min", "0", "step", "0.1"))
                        .build()));

        registry.registerType(new NodeTypeDef("VSlider", "Control", "V Slider", "UI", 84,
                new B(24, 200)
                        .add("value", PropertyType.FLOAT, "0", "Value", "Range", 0, Map.of("step", "0.01"))
                        .add("min_value", PropertyType.FLOAT, "0", "Min", "Range", 1, Map.of("step", "1"))
                        .add("max_value", PropertyType.FLOAT, "100", "Max", "Range", 2, Map.of("step", "1"))
                        .add("step", PropertyType.FLOAT, "1", "Step", "Range", 3, Map.of("min", "0", "step", "0.1"))
                        .build()));

        registry.registerType(new NodeTypeDef("LineEdit", "Control", "Line Edit", "UI", 85,
                new B(200, 32)
                        .add("text", PropertyType.STRING, "", "Text", "Content", 0, Map.of())
                        .add("placeholder", PropertyType.STRING, "", "Placeholder", "Content", 1, Map.of())
                        .add("max_length", PropertyType.INT, "0", "Max Length", "Content", 2, Map.of("min", "0", "step", "1"))
                        .add("secret", PropertyType.BOOL, "false", "Password", "Content", 3, Map.of())
                        .add("editable", PropertyType.BOOL, "true", "Editable", "State", 0, Map.of())
                        .build()));

        registry.registerClass(PlainNode.class, "Node");
    }

    private static final class B {
        private final Map<String, PropertyDef> map = new LinkedHashMap<>();

        B(int defW, int defH) {
            add("visible",       PropertyType.BOOL,  "true",              "Visible",    "Editor",    -1000, Map.of());
            add("editor_locked", PropertyType.BOOL,  "false",             "Locked",     "Editor",    -999,  Map.of());
            add("x",             PropertyType.FLOAT, "0",                 "X",          "Transform", 0,     Map.of("step", "0.5"));
            add("y",             PropertyType.FLOAT, "0",                 "Y",          "Transform", 1,     Map.of("step", "0.5"));
            add("rz",            PropertyType.FLOAT, "0",                 "Rotation",   "Transform", 2,     Map.of("step", "1"));
            add("sx",            PropertyType.FLOAT, "1",                 "Scale X",    "Transform", 3,     Map.of("min", "0.01", "step", "0.05"));
            add("sy",            PropertyType.FLOAT, "1",                 "Scale Y",    "Transform", 4,     Map.of("min", "0.01", "step", "0.05"));
            add("w",             PropertyType.FLOAT, String.valueOf(defW), "Width",     "Size",      0,     Map.of("min", "1", "step", "1"));
            add("h",             PropertyType.FLOAT, String.valueOf(defH), "Height",    "Size",      1,     Map.of("min", "1", "step", "1"));
            add("anchor_left",   PropertyType.FLOAT, "0",                 "Left",       "Anchor",    0,     Map.of("min", "0", "max", "1", "step", "0.01"));
            add("anchor_right",  PropertyType.FLOAT, "0",                 "Right",      "Anchor",    1,     Map.of("min", "0", "max", "1", "step", "0.01"));
            add("anchor_top",    PropertyType.FLOAT, "0",                 "Top",        "Anchor",    2,     Map.of("min", "0", "max", "1", "step", "0.01"));
            add("anchor_bottom", PropertyType.FLOAT, "0",                 "Bottom",     "Anchor",    3,     Map.of("min", "0", "max", "1", "step", "0.01"));
            add("margin_left",   PropertyType.FLOAT, "0",                 "Left",       "Margin",    0,     Map.of("step", "1"));
            add("margin_right",  PropertyType.FLOAT, "0",                 "Right",      "Margin",    1,     Map.of("step", "1"));
            add("margin_top",    PropertyType.FLOAT, "0",                 "Top",        "Margin",    2,     Map.of("step", "1"));
            add("margin_bottom", PropertyType.FLOAT, "0",                 "Bottom",     "Margin",    3,     Map.of("step", "1"));
            add("modulate_r",    PropertyType.FLOAT, "1",                 "R",          "Modulate",  0,     Map.of("min", "0", "max", "1", "step", "0.01"));
            add("modulate_g",    PropertyType.FLOAT, "1",                 "G",          "Modulate",  1,     Map.of("min", "0", "max", "1", "step", "0.01"));
            add("modulate_b",    PropertyType.FLOAT, "1",                 "B",          "Modulate",  2,     Map.of("min", "0", "max", "1", "step", "0.01"));
            add("modulate_a",    PropertyType.FLOAT, "1",                 "A",          "Modulate",  3,     Map.of("min", "0", "max", "1", "step", "0.01"));
            add("script",        PropertyType.STRING, null,               "Script",     "Script",    100,   Map.of());
        }

        B add(String key, PropertyType type, String def, String display, String category, int order, Map<String, String> hints) {
            map.put(key, new PropertyDef(key, type, def, display, category, order, hints));
            return this;
        }

        Map<String, PropertyDef> build() {
            return Collections.unmodifiableMap(map);
        }
    }
}
