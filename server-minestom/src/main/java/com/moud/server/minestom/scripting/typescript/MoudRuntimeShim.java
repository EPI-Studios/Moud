package com.moud.server.minestom.scripting.typescript;

import com.moud.core.NodeTypeDef;
import com.moud.core.NodeTypeRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Loads {@code moud-runtime.js} from the classpath and injects two generated
 * sections derived from the {@link NodeTypeRegistry}:
 * <ul>
 *   <li>{@code // __MOUD_CLASS_HIERARCHY__} — {@code var X = extend(Y)} declarations
 *       for every registered type, in topological order (parents before children).</li>
 *   <li>{@code // __MOUD_TYPE_MAP__} — {@code buildTypeClassMap()}, {@code NodeType}
 *       enum, and the {@code globalThis.__MoudRuntimeExports} assignment.</li>
 * </ul>
 * Adding a new node type to {@code CoreNodeTypesProvider} is all that is needed —
 * the runtime JS updates automatically on the next server start.
 */
public final class MoudRuntimeShim {

    private static final String RESOURCE_PATH = "/scripting/moud-runtime.js";
    private static final String MARKER_HIERARCHY = "    // __MOUD_CLASS_HIERARCHY__";
    private static final String MARKER_TYPE_MAP  = "    // __MOUD_TYPE_MAP__";

    private final String source;

    public MoudRuntimeShim(NodeTypeRegistry registry) {
        String template = load();
        String hierarchy = generateHierarchy(registry);
        String typeMap   = generateTypeMap(registry);
        this.source = template
                .replace(MARKER_HIERARCHY, hierarchy)
                .replace(MARKER_TYPE_MAP,  typeMap);
    }

    public String source() {
        return source;
    }

    // -------------------------------------------------------------------------

    private static String generateHierarchy(NodeTypeRegistry registry) {
        Map<String, NodeTypeDef> types = new TreeMap<>(registry.types());
        Map<String, String> parentOf = buildParentMap(types);
        List<String> ordered = topologicalSort(types.keySet(), parentOf);

        StringBuilder sb = new StringBuilder();
        sb.append("    // node class hierarchy — auto-generated from NodeTypeRegistry\n");
        for (String typeId : ordered) {
            if ("Node".equals(typeId)) continue; // Node is declared statically
            String parent = parentOf.getOrDefault(typeId, "Node");
            sb.append("    var ").append(typeId).append(" = extend(").append(parent).append(");\n");
        }
        return sb.toString();
    }

    private static String generateTypeMap(NodeTypeRegistry registry) {
        Map<String, NodeTypeDef> types = new TreeMap<>(registry.types());
        List<String> typeIds = new ArrayList<>(types.keySet());
        typeIds.sort(null);

        StringBuilder sb = new StringBuilder();
        sb.append("    // type map — auto-generated from NodeTypeRegistry\n");

        // buildTypeClassMap
        sb.append("    var __typeClassMap = null;\n");
        sb.append("    function buildTypeClassMap() {\n");
        sb.append("        __typeClassMap = {\n");
        for (String id : typeIds) {
            sb.append("            '").append(id).append("': ").append(id).append(",\n");
        }
        sb.append("        };\n");
        sb.append("    }\n\n");

        // NodeType enum
        sb.append("    var NodeType = {\n");
        for (String id : typeIds) {
            sb.append("        ").append(id).append(": '").append(id).append("',\n");
        }
        sb.append("    };\n\n");

        // __MoudRuntimeExports — all node classes + static API surface
        sb.append("    globalThis.__MoudRuntimeExports = {\n");
        // node classes
        for (String id : typeIds) {
            sb.append("        ").append(id).append(": ").append(id).append(",\n");
        }
        // enums
        sb.append("        NodeType: NodeType,\n");
        sb.append("        Shape: Shape,\n");
        sb.append("        InputAction: InputAction,\n");
        // decorators
        sb.append("        process: process, ready: ready, enterTree: enterTree,\n");
        sb.append("        exitTree: exitTree, physicsProcess: physicsProcess,\n");
        sb.append("        input: input, property: property, signal: signal, emits: emits,\n");
        // math
        sb.append("        Vec3: Vec3, lerp: lerp, clamp: clamp, randf: randf, randi: randi,\n");
        sb.append("        InstanceData: InstanceData,\n");
        // timers
        sb.append("        after: after,\n");
        // physics queries
        sb.append("        raycast: raycast, overlapSphere: overlapSphere,\n");
        // players
        sb.append("        getPlayers: getPlayers, teleportPlayer: teleportPlayer,\n");
        // cursor
        sb.append("        setCursorMode: setCursorMode, isCursorModeEnabled: isCursorModeEnabled,\n");
        sb.append("        setOsCursorVisible: setOsCursorVisible, isOsCursorVisible: isOsCursorVisible,\n");
        sb.append("        getCursorPosition: getCursorPosition,\n");
        // scene
        sb.append("        loadScene: loadScene, instantiate: instantiate,\n");
        sb.append("        getRoot: getRoot, findNodesByType: findNodesByType,\n");
        sb.append("    };\n");

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Topology helpers (mirrors ScriptTypeGenerator logic)
    // -------------------------------------------------------------------------

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
        for (String type : sorted) visit(type, parentOf, visited, result);
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

    // -------------------------------------------------------------------------

    private static String load() {
        try (InputStream in = MoudRuntimeShim.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException(
                        "moud-runtime.js not found on classpath at " + RESOURCE_PATH);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load moud-runtime.js", e);
        }
    }
}
