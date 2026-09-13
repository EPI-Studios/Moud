package com.meekdev.moud.addon.revo;

import com.meekdev.moud.core.addon.Addon;
import com.meekdev.moud.script.engine.LanguageAddon;
import com.meekdev.moud.script.engine.ScriptLanguage;

public final class RevoAddon implements Addon, LanguageAddon {

    @Override
    public String id() {
        return "moud-revo";
    }

    @Override
    public ScriptLanguage language() {
        return new RevoLanguage();
    }
}
