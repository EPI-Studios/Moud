package com.moud.plugin.api.ui;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public final class UIOverlayDefinition {
    private final String id;
    private final String type;
    private final String parentId;
    private final Map<String, Object> props;

    public UIOverlayDefinition(String type, Map<String, Object> props) {
        this(null, type, null, props);
    }

    public UIOverlayDefinition(String id, String type, String parentId, Map<String, Object> props) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("UIOverlayDefinition type cannot be null or empty");
        }
        this.id = id == null || id.isBlank() ? "srv_ui_" + UUID.randomUUID() : id;
        this.type = type;
        this.parentId = parentId;
        this.props = props == null ? Collections.emptyMap() : Collections.unmodifiableMap(props);
    }

    public String id() {
        return id;
    }

    public String type() {
        return type;
    }

    public String parentId() {
        return parentId;
    }

    public Map<String, Object> props() {
        return props;
    }
}
