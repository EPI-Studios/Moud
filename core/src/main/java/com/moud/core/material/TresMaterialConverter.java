package com.moud.core.material;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.GsonBuilder;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TresMaterialConverter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final Pattern GD_RESOURCE = Pattern.compile(
            "\\[gd_resource\\s+type=\"([^\"]+)\"");
    private static final Pattern EXT_RESOURCE = Pattern.compile(
            "\\[ext_resource\\s+.*?path=\"([^\"]+)\".*?id=\"?([^\"\\]\\s]+)\"?\\s*\\]");
    private static final Pattern RESOURCE_SECTION = Pattern.compile(
            "^\\[resource\\]\\s*$");
    private static final Pattern KEY_VALUE = Pattern.compile(
            "^([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*(.+)$");
    private static final Pattern COLOR_VALUE = Pattern.compile(
            "Color\\(\\s*([\\d.]+)\\s*,\\s*([\\d.]+)\\s*,\\s*([\\d.]+)\\s*(?:,\\s*([\\d.]+))?\\s*\\)");
    private static final Pattern VECTOR2_VALUE = Pattern.compile(
            "Vector2\\(\\s*([\\d.eE+-]+)\\s*,\\s*([\\d.eE+-]+)\\s*\\)");
    private static final Pattern VECTOR3_VALUE = Pattern.compile(
            "Vector3\\(\\s*([\\d.eE+-]+)\\s*,\\s*([\\d.eE+-]+)\\s*,\\s*([\\d.eE+-]+)\\s*\\)");
    private static final Pattern EXT_RESOURCE_REF = Pattern.compile(
            "ExtResource\\(\\s*\"?([^\"\\)]+)\"?\\s*\\)");

    private static final Map<String, String> FLOAT_PROPS = Map.ofEntries(
            Map.entry("roughness", "roughness"),
            Map.entry("metallic", "metallic"),
            Map.entry("metallic_specular", "specular"),
            Map.entry("specular_mode", "specular_mode"),
            Map.entry("clearcoat", "clearcoat"),
            Map.entry("clearcoat_roughness", "clearcoat_roughness"),
            Map.entry("anisotropy", "anisotropy"),
            Map.entry("rim", "rim"),
            Map.entry("rim_tint", "rim_tint"),
            Map.entry("refraction_scale", "refraction"),
            Map.entry("normal_scale", "normal_scale"),
            Map.entry("ao_light_affect", "ao_light_affect"),
            Map.entry("emission_energy_multiplier", "emission_energy"),
            Map.entry("emission_energy", "emission_energy"),
            Map.entry("alpha_scissor_threshold", "alpha_scissor_threshold"),
            Map.entry("point_size", "point_size"),
            Map.entry("heightmap_scale", "heightmap_scale")
    );

    private static final Map<String, String> INT_PROPS = Map.ofEntries(
            Map.entry("roughness_texture_channel", "roughness_texture_channel"),
            Map.entry("heightmap_min_layers", "heightmap_min_layers"),
            Map.entry("heightmap_max_layers", "heightmap_max_layers")
    );

    private static final Map<String, String> COLOR_PROPS = Map.ofEntries(
            Map.entry("albedo_color", "albedo_color"),
            Map.entry("emission", "emission_color"),
            Map.entry("backlight", "backlight_color")
    );

    private static final Map<String, String> TEXTURE_PROPS = Map.ofEntries(
            Map.entry("albedo_texture", "albedo_texture"),
            Map.entry("metallic_texture", "metallic_texture"),
            Map.entry("roughness_texture", "roughness_texture"),
            Map.entry("normal_texture", "normal_texture"),
            Map.entry("emission_texture", "emission_texture"),
            Map.entry("ao_texture", "ao_texture"),
            Map.entry("heightmap_texture", "heightmap_texture"),
            Map.entry("clearcoat_texture", "clearcoat_texture"),
            Map.entry("rim_texture", "rim_texture"),
            Map.entry("anisotropy_flowmap", "anisotropy_flowmap"),
            Map.entry("orm_texture", "orm_texture")
    );

    private static final Map<String, String> BOOL_PROPS = Map.ofEntries(
            Map.entry("normal_enabled", "normal_enabled"),
            Map.entry("emission_enabled", "emission_enabled"),
            Map.entry("ao_enabled", "ao_enabled"),
            Map.entry("heightmap_enabled", "heightmap_enabled"),
            Map.entry("heightmap_deep_parallax", "heightmap_deep_parallax"),
            Map.entry("clearcoat_enabled", "clearcoat_enabled"),
            Map.entry("rim_enabled", "rim_enabled"),
            Map.entry("anisotropy_enabled", "anisotropy_enabled"),
            Map.entry("refraction_enabled", "refraction_enabled"),
            Map.entry("backlight_enabled", "backlight_enabled")
    );

    private TresMaterialConverter() {
    }

    public record ConvertResult(String moudmatJson, String shader, String warnings,
                                List<String> referencedTextures) {}

    public static ConvertResult convert(String tresContent, String defaultShader) {
        if (tresContent == null || tresContent.isBlank()) return null;

        String[] lines = tresContent.replace("\r\n", "\n").replace('\r', '\n').split("\n");

        String resourceType = null;
        for (String line : lines) {
            Matcher m = GD_RESOURCE.matcher(line);
            if (m.find()) {
                resourceType = m.group(1);
                break;
            }
        }
        if (resourceType == null) return null;
        if (!resourceType.equals("StandardMaterial3D")
                && !resourceType.equals("ORMMaterial3D")
                && !resourceType.equals("BaseMaterial3D")) {
            return null;
        }

        Map<String, String> extResources = new HashMap<>();
        for (String line : lines) {
            Matcher m = EXT_RESOURCE.matcher(line);
            if (m.find()) {
                String path = m.group(1);
                String id = m.group(2);
                extResources.put(id, path);
            }
        }

        LinkedHashMap<String, String> rawProps = new LinkedHashMap<>();
        boolean inResource = false;
        for (String line : lines) {
            if (RESOURCE_SECTION.matcher(line).matches()) {
                inResource = true;
                continue;
            }
            if (line.startsWith("[") && inResource) {
                break;
            }
            if (!inResource) continue;

            Matcher kv = KEY_VALUE.matcher(line.trim());
            if (kv.matches()) {
                rawProps.put(kv.group(1), kv.group(2).trim());
            }
        }

        JsonObject params = new JsonObject();
        StringBuilder warnings = new StringBuilder();

        for (Map.Entry<String, String> entry : FLOAT_PROPS.entrySet()) {
            String raw = rawProps.get(entry.getKey());
            if (raw != null) {
                try {
                    params.addProperty(entry.getValue(), Float.parseFloat(raw));
                } catch (NumberFormatException ignored) {
                    warnings.append("Could not parse float: ").append(entry.getKey())
                            .append(" = ").append(raw).append('\n');
                }
            }
        }

        for (Map.Entry<String, String> entry : INT_PROPS.entrySet()) {
            String raw = rawProps.get(entry.getKey());
            if (raw != null) {
                try {
                    params.addProperty(entry.getValue(), Integer.parseInt(raw));
                } catch (NumberFormatException ignored) {
                    warnings.append("Could not parse int: ").append(entry.getKey())
                            .append(" = ").append(raw).append('\n');
                }
            }
        }

        for (Map.Entry<String, String> entry : COLOR_PROPS.entrySet()) {
            String raw = rawProps.get(entry.getKey());
            if (raw != null) {
                Matcher cm = COLOR_VALUE.matcher(raw);
                if (cm.find()) {
                    JsonArray arr = new JsonArray();
                    arr.add(Float.parseFloat(cm.group(1)));
                    arr.add(Float.parseFloat(cm.group(2)));
                    arr.add(Float.parseFloat(cm.group(3)));
                    if (cm.group(4) != null) {
                        arr.add(Float.parseFloat(cm.group(4)));
                    } else {
                        arr.add(1.0f);
                    }
                    params.add(entry.getValue(), arr);
                }
            }
        }

        for (Map.Entry<String, String> entry : BOOL_PROPS.entrySet()) {
            String raw = rawProps.get(entry.getKey());
            if (raw != null) {
                params.addProperty(entry.getValue(), "true".equalsIgnoreCase(raw.trim()));
            }
        }

        boolean ormMaterial = "ORMMaterial3D".equals(resourceType);
        if (ormMaterial) {
            params.addProperty("orm_enabled", true);
            if (!rawProps.containsKey("roughness_texture_channel")) {
                params.addProperty("roughness_texture_channel", 1);
            }
        }

        for (Map.Entry<String, String> entry : TEXTURE_PROPS.entrySet()) {
            String raw = rawProps.get(entry.getKey());
            if (raw != null) {
                Matcher extRef = EXT_RESOURCE_REF.matcher(raw);
                if (extRef.find()) {
                    String id = extRef.group(1).trim();
                    String path = extResources.get(id);
                    if (path != null) {
                        String moudPath = convertTexturePath(path);
                        JsonObject texObj = new JsonObject();
                        texObj.addProperty("type", "texture");
                        texObj.addProperty("value", moudPath);
                        params.add(entry.getValue(), texObj);
                    } else {
                        warnings.append("Unresolved texture reference: ")
                                .append(entry.getKey()).append(" → ExtResource(").append(id).append(")\n");
                    }
                }
            }
        }

        String uv1Scale = rawProps.get("uv1_scale");
        if (uv1Scale != null) {
            Matcher v3 = VECTOR3_VALUE.matcher(uv1Scale);
            Matcher v2 = VECTOR2_VALUE.matcher(uv1Scale);
            if (v3.find()) {
                JsonArray arr = new JsonArray();
                arr.add(Float.parseFloat(v3.group(1)));
                arr.add(Float.parseFloat(v3.group(2)));
                params.add("uv_scale", arr);
            } else if (v2.find()) {
                JsonArray arr = new JsonArray();
                arr.add(Float.parseFloat(v2.group(1)));
                arr.add(Float.parseFloat(v2.group(2)));
                params.add("uv_scale", arr);
            }
        }

        String uv1Offset = rawProps.get("uv1_offset");
        if (uv1Offset != null) {
            Matcher v3 = VECTOR3_VALUE.matcher(uv1Offset);
            Matcher v2 = VECTOR2_VALUE.matcher(uv1Offset);
            if (v3.find()) {
                JsonArray arr = new JsonArray();
                arr.add(Float.parseFloat(v3.group(1)));
                arr.add(Float.parseFloat(v3.group(2)));
                params.add("uv_offset", arr);
            } else if (v2.find()) {
                JsonArray arr = new JsonArray();
                arr.add(Float.parseFloat(v2.group(1)));
                arr.add(Float.parseFloat(v2.group(2)));
                params.add("uv_offset", arr);
            }
        }

        String transparency = rawProps.get("transparency");
        if (transparency != null && !transparency.isBlank()) {
            try {
                int mode = Integer.parseInt(transparency.trim());
                if (mode > 0) {
                    params.addProperty("transparency", mode);
                }
            } catch (NumberFormatException e) {
                params.addProperty("transparency_mode", transparency.trim().toLowerCase());
            }
        }

        String cullMode = rawProps.get("cull_mode");
        if (cullMode != null && !cullMode.isBlank()) {
            try {
                int mode = Integer.parseInt(cullMode.trim());
                if (mode == 2) params.addProperty("double_sided", true);
            } catch (NumberFormatException ignored) {}
        }

        String shader = defaultShader != null && !defaultShader.isBlank() ? defaultShader : "pbr";
        JsonObject root = new JsonObject();
        root.addProperty("shader", shader);
        root.add("params", params);

        String warningStr = warnings.isEmpty() ? "" : warnings.toString().trim();
        List<String> referencedTextures = new ArrayList<>(extResources.values());

        return new ConvertResult(GSON.toJson(root), shader, warningStr, List.copyOf(referencedTextures));
    }

    private static String convertTexturePath(String godotPath) {
        if (godotPath == null || godotPath.isBlank()) return "";
        String path = godotPath.trim();
        if (path.startsWith("res://")) {
            path = path.substring(6);
        }
        int slash = path.lastIndexOf('/');
        String filename = slash >= 0 ? path.substring(slash + 1) : path;
        return "res://textures/" + filename;
    }
}
