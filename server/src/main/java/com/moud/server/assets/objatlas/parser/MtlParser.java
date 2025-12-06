package com.moud.server.assets.objatlas.parser;

import com.moud.server.assets.objatlas.obj.ObjMaterial;
import com.moud.server.assets.objatlas.texture.RgbColor;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple MTL parser supporting common properties:
 * newmtl, Ka, Kd, Ks, Ke, Ns, Ni, d, Tr, illum, map_Kd, map_Ka, map_Ks, map_Bump/bump.
 */
public final class MtlParser {

    public Map<String, ObjMaterial> parse(Path mtlPath) throws IOException {
        Map<String, ObjMaterial> materials = new LinkedHashMap<>();

        String currentName = null;
        RgbColor ambient = null;
        RgbColor diffuse = null;
        RgbColor specular = null;
        RgbColor emissive = null;
        float shininess = 0.0f;
        float opticalDensity = 1.0f;
        float dissolve = 1.0f;
        int illum = -1;
        String mapKd = null;
        String mapKa = null;
        String mapKs = null;
        String mapBump = null;

        try (BufferedReader reader = Files.newBufferedReader(mtlPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String[] tokens = trimmed.split("\\s+");
                String keyword = tokens[0];

                switch (keyword) {
                    case "newmtl" -> {
                        // Flush previous material
                        if (currentName != null) {
                            ObjMaterial mat = new ObjMaterial(
                                    currentName,
                                    ambient,
                                    diffuse,
                                    specular,
                                    emissive,
                                    shininess,
                                    opticalDensity,
                                    dissolve,
                                    illum,
                                    mapKd,
                                    mapKa,
                                    mapKs,
                                    mapBump
                            );
                            materials.put(currentName, mat);
                        }

                        // Start new material
                        currentName = tokens.length > 1 ? tokens[1] : null;
                        ambient = null;
                        diffuse = null;
                        specular = null;
                        emissive = null;
                        shininess = 0.0f;
                        opticalDensity = 1.0f;
                        dissolve = 1.0f;
                        illum = -1;
                        mapKd = null;
                        mapKa = null;
                        mapKs = null;
                        mapBump = null;
                    }
                    case "Ka" -> ambient = parseColor(tokens);
                    case "Kd" -> diffuse = parseColor(tokens);
                    case "Ks" -> specular = parseColor(tokens);
                    case "Ke" -> emissive = parseColor(tokens);
                    case "Ns" -> shininess = parseFloat(tokens, 1, shininess);
                    case "Ni" -> opticalDensity = parseFloat(tokens, 1, opticalDensity);
                    case "d" -> dissolve = parseFloat(tokens, 1, dissolve);
                    case "Tr" -> {
                        // Tr is transparency, usually 1 - d
                        float tr = parseFloat(tokens, 1, 1.0f - dissolve);
                        dissolve = 1.0f - tr;
                    }
                    case "illum" -> illum = (int) parseFloat(tokens, 1, illum);
                    case "map_Kd" -> mapKd = parseMapName(tokens);
                    case "map_Ka" -> mapKa = parseMapName(tokens);
                    case "map_Ks" -> mapKs = parseMapName(tokens);
                    case "map_Bump", "map_bump", "bump" -> mapBump = parseMapName(tokens);
                    default -> {
                        // Unknown/unsupported keyword: ignore
                    }
                }
            }
        }

        // Flush last material
        if (currentName != null) {
            ObjMaterial mat = new ObjMaterial(
                    currentName,
                    ambient,
                    diffuse,
                    specular,
                    emissive,
                    shininess,
                    opticalDensity,
                    dissolve,
                    illum,
                    mapKd,
                    mapKa,
                    mapKs,
                    mapBump
            );
            materials.put(currentName, mat);
        }

        return materials;
    }

    private RgbColor parseColor(String[] tokens) {
        float r = tokens.length > 1 ? parseFloat(tokens[1], 0.0f) : 0.0f;
        float g = tokens.length > 2 ? parseFloat(tokens[2], 0.0f) : 0.0f;
        float b = tokens.length > 3 ? parseFloat(tokens[3], 0.0f) : 0.0f;
        return new RgbColor(r, g, b);
    }

    private float parseFloat(String[] tokens, int index, float defaultValue) {
        if (tokens.length <= index) {
            return defaultValue;
        }
        return parseFloat(tokens[index], defaultValue);
    }

    private float parseFloat(String value, float defaultValue) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String parseMapName(String[] tokens) {
        // Map names can have options like "-blendu on", we keep it simple and take the last token
        if (tokens.length < 2) return null;
        return tokens[tokens.length - 1];
    }
}
