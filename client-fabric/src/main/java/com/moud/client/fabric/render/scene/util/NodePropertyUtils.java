package com.moud.client.fabric.render.scene.util;

import com.moud.client.fabric.scene.ClientPropertyOverrides;
import com.moud.net.protocol.SceneSnapshot;
import java.util.List;

public final class NodePropertyUtils {
    private NodePropertyUtils() {
    }

    public static float parseFloat(String value, float fallback) {
        try {
            if (value == null) {
                return fallback;
            }
            float parsed = Float.parseFloat(value.trim());
            return Float.isFinite(parsed) ? parsed : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static boolean parseBool(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase();
        if ("true".equals(normalized) || "1".equals(normalized) || "t".equals(normalized)
                || "yes".equals(normalized) || "y".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "f".equals(normalized)
                || "no".equals(normalized) || "n".equals(normalized)) {
            return false;
        }
        return fallback;
    }

    public static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public static String stringProp(SceneSnapshot.NodeSnapshot node, String key) {
        if (node == null || key == null) {
            return null;
        }
        String override = ClientPropertyOverrides.get(node.nodeId(), key);
        if (override != null) {
            return override;
        }
        List<SceneSnapshot.Property> props = node.properties();
        if (props == null || props.isEmpty()) {
            return null;
        }
        for (SceneSnapshot.Property prop : props) {
            if (prop != null && key.equals(prop.key())) {
                return prop.value();
            }
        }
        return null;
    }

    public static String normalizedTextMode(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase();
    }
}
