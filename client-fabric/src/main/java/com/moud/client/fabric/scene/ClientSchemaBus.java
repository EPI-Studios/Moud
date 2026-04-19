package com.moud.client.fabric.scene;

import com.moud.core.NodeTypeDef;
import com.moud.core.PropertyDef;
import com.moud.net.protocol.SchemaSnapshot;
import java.util.HashMap;
import java.util.Map;

public final class ClientSchemaBus {
    private static volatile Map<String, NodeTypeDef> typesById = Map.of();

    private ClientSchemaBus() {
    }

    public static void set(SchemaSnapshot schema) {
        if (schema == null || schema.types() == null) {
            typesById = Map.of();
            return;
        }
        HashMap<String, NodeTypeDef> next = new HashMap<>();
        for (NodeTypeDef def : schema.types()) {
            if (def != null && def.typeId() != null) next.put(def.typeId(), def);
        }
        typesById = Map.copyOf(next);
    }

    public static void clear() {
        typesById = Map.of();
    }

    public static NodeTypeDef typeOf(String typeId) {
        return typeId == null ? null : typesById.get(typeId);
    }

    public static String defaultOf(String typeId, String propertyKey, String fallback) {
        NodeTypeDef def = typeOf(typeId);
        if (def == null || def.properties() == null) return fallback;
        PropertyDef prop = def.properties().get(propertyKey);
        if (prop == null) return fallback;
        String v = prop.defaultValue();
        return v == null ? fallback : v;
    }
}
