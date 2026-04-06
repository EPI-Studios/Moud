package com.moud.server.minestom.scripting;

import java.util.EnumMap;
import java.util.Map;

final class ScriptLanguageRegistry {
    private final Map<ScriptLanguage, ScriptLanguageSupport> supportByLanguage;

    ScriptLanguageRegistry() {
        EnumMap<ScriptLanguage, ScriptLanguageSupport> map = new EnumMap<>(ScriptLanguage.class);
        map.put(ScriptLanguage.JAVASCRIPT, new ScriptLanguageSupport(
                ScriptLanguage.JAVASCRIPT,
                true,
                ""
        ));
        map.put(ScriptLanguage.TYPESCRIPT, new ScriptLanguageSupport(
                ScriptLanguage.TYPESCRIPT,
                true,
                ""
        ));
        map.put(ScriptLanguage.LUAU, buildLuauSupport());
        map.put(ScriptLanguage.UNKNOWN, new ScriptLanguageSupport(
                ScriptLanguage.UNKNOWN,
                false,
                "Unsupported script language"
        ));
        supportByLanguage = Map.copyOf(map);
    }

    ScriptLanguageSupport supportFor(ScriptLanguage language) {
        if (language == null) {
            return supportByLanguage.get(ScriptLanguage.UNKNOWN);
        }
        return supportByLanguage.getOrDefault(language, supportByLanguage.get(ScriptLanguage.UNKNOWN));
    }

    private static ScriptLanguageSupport buildLuauSupport() {
        if (!Boolean.getBoolean("moud.server.enableLuau")) {
            return new ScriptLanguageSupport(
                    ScriptLanguage.LUAU,
                    false,
                    "Luau support is not enabled in this server build"
            );
        }
        int javaVersion = Runtime.version().feature();
        if (javaVersion < 25) {
            return new ScriptLanguageSupport(
                    ScriptLanguage.LUAU,
                    false,
                    "Luau scripts require Java 25+ but the current server runtime is Java " + javaVersion
            );
        }
        if (!LuauRuntimeBridge.isRuntimeLinked()) {
            String problem = LuauRuntimeBridge.runtimeLinkProblem();
            return new ScriptLanguageSupport(
                    ScriptLanguage.LUAU,
                    false,
                    "Luau support is enabled, but luau-java failed to link: " + (problem == null ? "unknown problem" : problem)
            );
        }
        return new ScriptLanguageSupport(
                ScriptLanguage.LUAU,
                true,
                ""
        );
    }
}
