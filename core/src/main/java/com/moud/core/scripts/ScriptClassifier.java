package com.moud.core.scripts;

import java.util.Locale;

public final class ScriptClassifier {
    private static final String SUFFIX_SERVER = ".server.luau";
    private static final String SUFFIX_CLIENT = ".client.luau";
    private static final String SUFFIX_SHARED = ".shared.luau";
    private static final String SUFFIX_LUAU = ".luau";
    private static final String[] SERVER_ONLY_EXTS = {".ts", ".mts", ".js", ".mjs", ".cjs"};

    private ScriptClassifier() {
    }

    public static ScriptSide sideOf(String path) {
        if (path == null) return null;
        String lower = path.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) return null;
        if (lower.endsWith(SUFFIX_SHARED)) return ScriptSide.SHARED;
        if (lower.endsWith(SUFFIX_CLIENT)) return ScriptSide.CLIENT;
        if (lower.endsWith(SUFFIX_SERVER)) return ScriptSide.SERVER;
        if (lower.endsWith(SUFFIX_LUAU)) return ScriptSide.SERVER;
        for (String ext : SERVER_ONLY_EXTS) {
            if (lower.endsWith(ext)) return ScriptSide.SERVER;
        }
        return null;
    }

    public static boolean isScriptPath(String path) {
        return sideOf(path) != null;
    }

    public static boolean isValidFor(String path, ScriptSlot slot) {
        ScriptSide side = sideOf(path);
        return slot != null && slot.accepts(side);
    }
}
