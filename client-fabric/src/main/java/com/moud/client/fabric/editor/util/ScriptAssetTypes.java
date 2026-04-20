package com.moud.client.fabric.editor.util;

import java.util.Locale;

public final class ScriptAssetTypes {

    public enum Kind {
        TYPESCRIPT(".ts", "new_script.js"),
        JAVASCRIPT(".js", "new_script.js"),
        LUAU(".luau", "new_script.luau"),
        JAVA(".java", "new_script.java");

        private final String extension;
        private final String templateResource;

        Kind(String extension, String templateResource) {
            this.extension = extension;
            this.templateResource = templateResource;
        }

        public String extension() {
            return extension;
        }

        public String templateResource() {
            return templateResource;
        }
    }

    public static final String DEFAULT_EXTENSION = ".ts";
    public static final String FILTER_LABEL = "Script (.ts, .js, .luau, .java)";

    private ScriptAssetTypes() { }

    public static boolean isScriptPath(String path) {
        return kindOf(path) != null;
    }

    public static Kind kindOf(String path) {
        if (path == null) return null;
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".luau")) return Kind.LUAU;
        if (lower.endsWith(".java")) return Kind.JAVA;
        if (lower.endsWith(".ts") || lower.endsWith(".mts")) return Kind.TYPESCRIPT;
        if (lower.endsWith(".js") || lower.endsWith(".mjs") || lower.endsWith(".cjs")) return Kind.JAVASCRIPT;
        return null;
    }

    public static String ensureExtension(String filename) {
        if (filename == null || filename.isBlank()) return filename;
        if (isScriptPath(filename)) return filename;
        return filename + DEFAULT_EXTENSION;
    }

    public static Kind kindOrDefault(String path) {
        Kind kind = kindOf(path);
        return kind == null ? Kind.TYPESCRIPT : kind;
    }
}
