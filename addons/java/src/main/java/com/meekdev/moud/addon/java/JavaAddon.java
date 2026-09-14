package com.meekdev.moud.addon.java;

import com.meekdev.moud.core.addon.Addon;
import com.meekdev.moud.script.engine.LanguageAddon;
import com.meekdev.moud.script.engine.ScriptLanguage;

public final class JavaAddon implements Addon, LanguageAddon {

    @Override
    public String id() {
        return "moud-java";
    }

    @Override
    public ScriptLanguage language() {
        return new JavaLanguage();
    }
}
