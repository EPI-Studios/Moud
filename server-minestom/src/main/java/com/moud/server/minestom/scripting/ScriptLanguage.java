package com.moud.server.minestom.scripting;

import java.util.Locale;

enum ScriptLanguage {
    JAVASCRIPT("JavaScript"),
    TYPESCRIPT("TypeScript"),
    LUAU("Luau"),
    UNKNOWN("Unknown");

    private final String displayName;

    ScriptLanguage(String displayName) {
        this.displayName = displayName;
    }

    String displayName() {
        return displayName;
    }

    static ScriptLanguage fromPath(String path) {
        if (path == null) {
            return UNKNOWN;
        }
        String value = path.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return UNKNOWN;
        }
        if (value.endsWith(".luau")) {
            return LUAU;
        }
        if (value.endsWith(".ts") || value.endsWith(".mts")) {
            return TYPESCRIPT;
        }
        if (value.endsWith(".js") || value.endsWith(".mjs") || value.endsWith(".cjs")) {
            return JAVASCRIPT;
        }
        int slash = value.lastIndexOf('/');
        int dot = value.lastIndexOf('.');
        if (dot <= slash) {
            return JAVASCRIPT;
        }
        return UNKNOWN;
    }
}
