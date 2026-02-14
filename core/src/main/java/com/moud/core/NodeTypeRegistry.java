package com.moud.core;

import com.moud.core.scene.Node;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


public final class NodeTypeRegistry {
    private final Map<String, NodeTypeDef> types = new HashMap<>();
    private final Map<Class<?>, String> typeIdsByClass = new HashMap<>();
    private boolean allowUnknownTypes = true;
    private boolean allowUnknownProperties = true;

    public NodeTypeRegistry setAllowUnknownTypes(boolean allowUnknownTypes) {
        this.allowUnknownTypes = allowUnknownTypes;
        return this;
    }

    public NodeTypeRegistry setAllowUnknownProperties(boolean allowUnknownProperties) {
        this.allowUnknownProperties = allowUnknownProperties;
        return this;
    }

    public void registerType(NodeTypeDef def) {
        Objects.requireNonNull(def, "def");
        types.put(def.typeId(), def);
    }

    public void registerClass(Class<?> clazz, String typeId) {
        Objects.requireNonNull(clazz, "clazz");
        Objects.requireNonNull(typeId, "typeId");
        typeIdsByClass.put(clazz, typeId);
    }

    public Map<String, NodeTypeDef> types() {
        return Collections.unmodifiableMap(types);
    }

    public NodeTypeDef getType(String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return null;
        }
        return types.get(typeId);
    }

    public String typeIdFor(Node node) {
        if (node == null) {
            return "Node";
        }
        String explicit = node.getProperty("@type");
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        Class<?> c = node.getClass();
        while (c != null) {
            String id = typeIdsByClass.get(c);
            if (id != null) {
                return id;
            }
            c = c.getSuperclass();
        }
        return node.getClass().getSimpleName();
    }

    public void applyDefaults(Node node, String typeId) {
        if (node == null) {
            return;
        }
        if (typeId == null || typeId.isBlank()) {
            typeId = "Node";
        }

        if (!"Node".equals(typeId)) {
            String explicit = node.getProperty("@type");
            if (explicit == null || explicit.isBlank()) {
                node.setProperty("@type", typeId);
            }
        }

        NodeTypeDef def = types.get(typeId);
        if (def == null) {
            return;
        }
        for (PropertyDef prop : def.properties().values()) {
            if (prop == null) {
                continue;
            }
            String key = prop.key();
            if (key == null || key.isBlank()) {
                continue;
            }
            if (node.getProperty(key) != null) {
                continue;
            }
            String dv = prop.defaultValue();
            if (dv == null) {
                continue;
            }
            node.setProperty(key, dv);
        }
    }

    public ValidationResult validateSetProperty(String typeId, String key, String value) {
        if (key == null || key.isBlank()) {
            return ValidationResult.failure("key empty");
        }
        NodeTypeDef def = types.get(typeId);
        if (def == null) {
            return allowUnknownTypes ? ValidationResult.success() : ValidationResult.failure("unknown type: " + typeId);
        }
        PropertyDef prop = def.properties().get(key);
        if (prop == null) {
            return allowUnknownProperties ? ValidationResult.success() : ValidationResult.failure("unknown property: " + key);
        }
        return prop.type().validate(value);
    }

    public ValidationResult validateRemoveProperty(String typeId, String key) {
        if (key == null || key.isBlank()) {
            return ValidationResult.failure("key empty");
        }
        NodeTypeDef def = types.get(typeId);
        if (def == null) {
            return allowUnknownTypes ? ValidationResult.success() : ValidationResult.failure("unknown type: " + typeId);
        }
        if (!def.properties().containsKey(key)) {
            return allowUnknownProperties ? ValidationResult.success() : ValidationResult.failure("unknown property: " + key);
        }
        return ValidationResult.success();
    }
}
