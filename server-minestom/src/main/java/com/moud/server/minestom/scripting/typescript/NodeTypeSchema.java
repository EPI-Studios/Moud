package com.moud.server.minestom.scripting.typescript;

import com.moud.core.NodeTypeDef;
import com.moud.core.NodeTypeRegistry;
import com.moud.core.PropertyDef;
import com.moud.core.PropertyType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// proprety type map from the node type registry for graalvm context
public final class NodeTypeSchema {

    private final Map<String, Map<String, String>> schema;

    public NodeTypeSchema(NodeTypeRegistry registry) {
        Map<String, Map<String, String>> built = new HashMap<>();
        for (NodeTypeDef typeDef : registry.types().values()) {
            Map<String, String> props = new HashMap<>();
            for (PropertyDef propDef : typeDef.properties().values()) {
                props.put(propDef.key(), jsTypeName(propDef.type()));
            }
            built.put(typeDef.typeId(), Collections.unmodifiableMap(props));
        }
        this.schema = Collections.unmodifiableMap(built);
    }

    public Map<String, Map<String, String>> schema() {
        return schema;
    }

    private static String jsTypeName(PropertyType type) {
        return switch (type) {
            case INT -> "int";
            case FLOAT -> "float";
            case BOOL -> "bool";
            default -> "string";
        };
    }
}
