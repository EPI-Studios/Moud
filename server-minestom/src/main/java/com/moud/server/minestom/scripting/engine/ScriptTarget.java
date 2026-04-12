package com.moud.server.minestom.scripting.engine;

import com.moud.server.minestom.scripting.ScriptLanguage;

public record ScriptTarget(long nodeId, String scriptPath, ScriptLanguage language) {
}
