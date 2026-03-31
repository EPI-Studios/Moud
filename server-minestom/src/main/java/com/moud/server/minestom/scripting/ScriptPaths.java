package com.moud.server.minestom.scripting;

final class ScriptPaths {
    private ScriptPaths() {
    }

    static String normalizeScriptPath(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        return value;
    }

    static ScriptReference parseScript(String raw) {
        String path = normalizeScriptPath(raw);
        if (path == null) {
            return null;
        }
        return new ScriptReference(path, ScriptLanguage.fromPath(path));
    }
}
