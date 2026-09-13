package com.meekdev.moud.mod.place;

import com.meekdev.moud.script.engine.ScriptLanguage;
import com.meekdev.moud.script.luau.LuauLanguage;
import java.util.ArrayList;
import java.util.List;

public final class Languages {

    private static final List<ScriptLanguage> KNOWN = new ArrayList<>(List.of(new LuauLanguage()));

    private Languages() {}

    public static void add(ScriptLanguage language) {
        for (ScriptLanguage already : KNOWN) {
            for (String extension : language.extensions()) {
                if (already.extensions().stream().anyMatch(extension::equalsIgnoreCase)) {
                    throw new IllegalStateException("two languages claim '." + extension + "': " + already.name() + " and " + language.name());
                }
            }
        }
        KNOWN.add(language);
    }

    public static List<ScriptLanguage> all() {
        return List.copyOf(KNOWN);
    }

    public static List<String> extensions() {
        return KNOWN.stream().flatMap(language -> language.extensions().stream()).toList();
    }
}
