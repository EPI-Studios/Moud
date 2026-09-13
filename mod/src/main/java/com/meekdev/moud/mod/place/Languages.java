package com.meekdev.moud.mod.place;

import com.meekdev.moud.script.engine.Luau;
import com.meekdev.moud.script.engine.ScriptLanguage;
import java.util.ArrayList;
import java.util.List;

public final class Languages {

    private static final List<ScriptLanguage> KNOWN = new ArrayList<>(List.of(new Luau()));

    private Languages() {}

    public static void add(ScriptLanguage language) {
        for (ScriptLanguage already : KNOWN) {
            if (already.extension().equalsIgnoreCase(language.extension())) {
                throw new IllegalStateException("two languages claim '." + language.extension()
                        + "': " + already.name() + " and " + language.name());
            }
        }
        KNOWN.add(language);
    }

    public static List<ScriptLanguage> all() {
        return List.copyOf(KNOWN);
    }
}
