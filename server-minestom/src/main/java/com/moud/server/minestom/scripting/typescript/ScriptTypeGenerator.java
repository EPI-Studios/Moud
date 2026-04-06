package com.moud.server.minestom.scripting.typescript;

import com.moud.core.NodeTypeDef;
import com.moud.core.NodeTypeRegistry;
import com.moud.core.PropertyDef;
import com.moud.core.PropertyType;
import com.moud.server.minestom.util.DebugLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ScriptTypeGenerator {

    private static final String LOG_TAG = "script-type-gen";

    private static final Map<String, String> NESTED_KEY_TO_GROUP = Map.of(
            "x", "position", "y", "position", "z", "position",
            "rx", "rotation", "ry", "rotation", "rz", "rotation",
            "sx", "scale", "sy", "scale", "sz", "scale"
    );

    private final NodeTypeRegistry registry;

    public ScriptTypeGenerator(NodeTypeRegistry registry) {
        this.registry = registry;
    }

    public void generate(Path outputPath) {
        String content = buildContent();
        try {
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, content, StandardCharsets.UTF_8);
            DebugLog.info(LOG_TAG, "generated " + outputPath.toAbsolutePath().normalize());
        } catch (IOException e) {
            DebugLog.error(LOG_TAG, "failed to write " + outputPath + ": " + e.getMessage(), e);
        }
    }


    private String buildContent() {
        StringBuilder sb = new StringBuilder();
        appendHeader(sb);
        appendNodeTypes(sb);
        return sb.toString();
    }

    private void appendNodeTypes(StringBuilder sb) {
        Map<String, NodeTypeDef> sorted = new TreeMap<>(registry.types());

        Map<String, String> parentOf = buildParentMap(sorted);

        List<String> ordered = topologicalSort(sorted.keySet(), parentOf);

        sb.append("// =============================================================================\n");
        sb.append("// AUTO-GENERATED NODE TYPES - do not edit, regenerated on server startup\n");
        sb.append("// =============================================================================\n");
        sb.append("declare module \"moud\" {\n");

        for (String typeId : ordered) {
            NodeTypeDef def = sorted.get(typeId);
            if (def == null) continue;
            String parent = parentOf.getOrDefault(typeId, "Node");
            appendNodeClass(sb, def, parent);
        }

        sb.append("}\n");
    }

    private void appendNodeClass(StringBuilder sb, NodeTypeDef def, String parent) {
        Set<String> emittedGroups = new HashSet<>();
        List<String> flatProps = new ArrayList<>();

        for (PropertyDef prop : def.properties().values()) {
            String group = NESTED_KEY_TO_GROUP.get(prop.key());
            if (group != null) {
                emittedGroups.add(group);
            } else if (isScriptVisible(prop)) {
                flatProps.add(prop.key());
            }
        }

        sb.append("  export class ").append(def.typeId());
        if (!def.typeId().equals(parent)) {
            sb.append(" extends ").append(parent);
        }
        sb.append(" {\n");

        for (String group : new String[]{"position", "rotation", "scale"}) {
            if (emittedGroups.contains(group)) {
                sb.append("    ").append(group).append(": Vector3;\n");
            }
        }

        flatProps.sort(null);
        for (String key : flatProps) {
            PropertyDef prop = def.properties().get(key);
            if (prop == null) continue;
            sb.append("    ").append(camelCase(key)).append(": ").append(tsType(prop.type())).append(";\n");
        }

        sb.append("  }\n");
    }


    private static Map<String, String> buildParentMap(Map<String, NodeTypeDef> types) {
        Map<String, String> parentOf = new LinkedHashMap<>();
        for (NodeTypeDef def : types.values()) {
            String pid = def.parentTypeId();
            if (pid != null && !pid.isBlank() && !pid.equals(def.typeId())) {
                parentOf.put(def.typeId(), pid);
            }
        }
        return parentOf;
    }

    private static List<String> topologicalSort(Set<String> types, Map<String, String> parentOf) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        List<String> sorted = new ArrayList<>(types);
        sorted.sort(null);
        for (String type : sorted) {
            visit(type, parentOf, visited, result);
        }
        return result;
    }

    private static void visit(String type, Map<String, String> parentOf,
                              Set<String> visited, List<String> result) {
        if (visited.contains(type)) return;
        visited.add(type);
        String parent = parentOf.get(type);
        if (parent != null) visit(parent, parentOf, visited, result);
        result.add(type);
    }

    private static boolean isScriptVisible(PropertyDef prop) {
        String key = prop.key();
        return !key.equals("script")
                && !key.equals("editor_locked")
                && !key.equals("solid")
                && !key.startsWith("color_tint_")
                && !key.startsWith("sky_")
                && !key.startsWith("clouds_")
                && !key.startsWith("cloud_")
                && !key.startsWith("fog_")
                && !key.startsWith("time_")
                && !key.equals("weather")
                && !key.equals("ambient_light");
    }

    private static String tsType(PropertyType type) {
        return switch (type) {
            case INT, FLOAT -> "number";
            case BOOL -> "boolean";
            default -> "string";
        };
    }

    private static String camelCase(String key) {
        if (!key.contains("_")) return key;
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : key.toCharArray()) {
            if (c == '_') { upper = true; }
            else if (upper) { sb.append(Character.toUpperCase(c)); upper = false; }
            else { sb.append(c); }
        }
        return sb.toString();
    }


    private static void appendHeader(StringBuilder sb) {
        try (InputStream in = ScriptTypeGenerator.class.getClassLoader()
                .getResourceAsStream("scripting/moud-header.d.ts")) {
            if (in == null) throw new IOException("scripting/moud-header.d.ts not found on classpath");
            sb.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load moud-header.d.ts", e);
        }
    }
}