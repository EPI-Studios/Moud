package com.moud.core.mesh.obj;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MtlParser {

    private MtlParser() {
    }

    public static Map<String, MtlMaterial> parse(String text) {
        LinkedHashMap<String, MtlMaterial> out = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        String currentName = null;
        float r = 1f, g = 1f, b = 1f, a = 1f;
        String diffuseTexture = null;

        for (String rawLine : text.split("\n")) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) continue;
            String[] tokens = line.split("\\s+");
            String head = tokens[0].toLowerCase();
            switch (head) {
                case "newmtl" -> {
                    if (currentName != null) {
                        out.put(currentName, new MtlMaterial(currentName, r, g, b, a, diffuseTexture));
                    }
                    currentName = tokens.length > 1 ? tokens[1] : "default";
                    r = 1f; g = 1f; b = 1f; a = 1f;
                    diffuseTexture = null;
                }
                case "kd" -> {
                    if (tokens.length >= 4) {
                        r = parse(tokens[1], 1f);
                        g = parse(tokens[2], 1f);
                        b = parse(tokens[3], 1f);
                    }
                }
                case "d" -> {
                    if (tokens.length >= 2) a = parse(tokens[1], 1f);
                }
                case "tr" -> {
                    if (tokens.length >= 2) a = 1f - parse(tokens[1], 0f);
                }
                case "map_kd" -> {
                    if (tokens.length >= 2) diffuseTexture = tokens[tokens.length - 1];
                }
                default -> {
                }
            }
        }

        if (currentName != null) {
            out.put(currentName, new MtlMaterial(currentName, r, g, b, a, diffuseTexture));
        }
        return out;
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash < 0 ? line : line.substring(0, hash);
    }

    private static float parse(String value, float fallback) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
