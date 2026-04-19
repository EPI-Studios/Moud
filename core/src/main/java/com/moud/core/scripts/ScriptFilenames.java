package com.moud.core.scripts;

import java.util.Locale;

public final class ScriptFilenames {
    private static final String SUFFIX_SERVER = ".server.luau";
    private static final String SUFFIX_CLIENT = ".client.luau";
    private static final String SUFFIX_SHARED = ".shared.luau";
    private static final String SUFFIX_LUAU = ".luau";

    private ScriptFilenames() {
    }

    public static String ensureSuffixFor(String filename, ScriptSlot slot) {
        if (filename == null || filename.isBlank() || slot == null) return filename;
        String lower = filename.toLowerCase(Locale.ROOT);
        ScriptSide side = ScriptClassifier.sideOf(filename);
        if (side != null && slot.accepts(side)) return filename;
        if (!lower.endsWith(SUFFIX_LUAU)) return filename;
        String stem = filename.substring(0, filename.length() - SUFFIX_LUAU.length());
        if (stem.toLowerCase(Locale.ROOT).endsWith(".client")
                || stem.toLowerCase(Locale.ROOT).endsWith(".server")
                || stem.toLowerCase(Locale.ROOT).endsWith(".shared")) {
            int dot = stem.lastIndexOf('.');
            stem = stem.substring(0, dot);
        }
        return stem + switch (slot) {
            case CLIENT_SCRIPT -> SUFFIX_CLIENT;
            case SCRIPT -> SUFFIX_SERVER;
        };
    }

    public static boolean isLuau(String filename) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(SUFFIX_LUAU);
    }
}
