package com.meekdev.moud.addon.java;

import com.meekdev.moud.script.engine.ScriptEngine;
import com.meekdev.moud.script.engine.ScriptLanguage;
import com.meekdev.moud.script.host.Host;
import java.util.List;

public final class JavaLanguage implements ScriptLanguage {

    @Override
    public String name() {
        return "java";
    }

    @Override
    public List<String> extensions() {
        return List.of("java");
    }

    @Override
    public ScriptEngine start(Host host) {
        return new JavaEngine(host);
    }
}
