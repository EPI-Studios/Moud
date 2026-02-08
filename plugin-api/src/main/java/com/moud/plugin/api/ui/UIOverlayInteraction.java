package com.moud.plugin.api.ui;

import java.util.Collections;
import java.util.Map;

public record UIOverlayInteraction(String elementId, String action, Map<String, Object> data) {
    public UIOverlayInteraction {
        data = data == null ? Collections.emptyMap() : Collections.unmodifiableMap(data);
    }
}
