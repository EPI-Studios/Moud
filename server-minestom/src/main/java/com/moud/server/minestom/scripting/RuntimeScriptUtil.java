package com.moud.server.minestom.scripting;


import com.moud.core.scene.Node;

final class RuntimeScriptUtil {
    private RuntimeScriptUtil() {
    }

    static String resolveOwnerUuid(Node node) {
        if (node == null) {
            return null;
        }
        String v = node.getProperty(RuntimeScriptKeys.OWNER_UUID_KEY);
        if (v == null || v.isBlank()) {
            v = node.getProperty(RuntimeScriptKeys.OWNER_KEY);
        }
        if (v == null || v.isBlank()) {
            v = node.getProperty(RuntimeScriptKeys.OWNER_KEY_ALT);
        }
        if (v == null) {
            return null;
        }
        String s = v.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(s).toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    static String trimFloat(float v) {
        if (!Float.isFinite(v)) {
            v = 0.0f;
        }
        if (Math.abs(v) < 1e-6f) {
            v = 0.0f;
        }
        String s = Float.toString(v);
        if (s.endsWith(".0")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }
}
