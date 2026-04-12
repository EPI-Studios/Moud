package com.moud.server.minestom.scripting.engine;

import com.moud.server.minestom.scripting.ScriptLanguage;
import com.moud.server.minestom.scripting.ScriptObject;
import com.moud.server.minestom.scripting.input.ScriptInputApi;


import java.nio.file.Path;

public final class RuntimeScriptInstance {
    public final long nodeId;
    public final Path scriptFile;
    public final ScriptLanguage language;
    public final long programModifiedMs;
    public final ScriptObject instance;
    public final Object api;
    public final ScriptTimingBudget budget = new ScriptTimingBudget();
    public boolean readyCalled;
    public boolean disabled;
    public long lastInputClientTick;
    public ScriptInputApi inputApi;

    public RuntimeScriptInstance(long nodeId, Path scriptFile, ScriptLanguage language, long programModifiedMs, ScriptObject instance, Object api) {
        this.nodeId = nodeId;
        this.scriptFile = scriptFile;
        this.language = language;
        this.programModifiedMs = programModifiedMs;
        this.instance = instance;
        this.api = api;
        this.lastInputClientTick = -1L;
    }
}
