package com.meekdev.moud.mod.addon;

import com.meekdev.moud.script.engine.ScriptLanguage;

// an addon that brings a language with it
//
// separate from Addon rather than a method on it, because Addon lives in core and core may not
// see a script type (5.2). an addon implements as many of these as it has things to add, and the
// loader asks each one what it is
public interface LanguageAddon {

    ScriptLanguage language();
}
