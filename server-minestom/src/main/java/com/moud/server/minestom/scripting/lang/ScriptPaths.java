package com.moud.server.minestom.scripting.lang;

import com.moud.server.minestom.scripting.ScriptLanguage;
import com.moud.server.minestom.scripting.ScriptReference;

public final class ScriptPaths {
    private ScriptPaths() {
    }

    public static String normalizeScriptPath(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        return value;
    }

    public static ScriptReference parseScript(String raw) {
        String path = normalizeScriptPath(raw);
        if (path == null) {
            return null;
        }
        return new ScriptReference(path, ScriptLanguage.fromPath(path));
    }
}
