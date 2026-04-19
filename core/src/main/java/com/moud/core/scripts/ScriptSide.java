package com.moud.core.scripts;

public enum ScriptSide {
    SERVER,
    CLIENT,
    SHARED;

    public boolean runsOnServer() {
        return this == SERVER || this == SHARED;
    }

    public boolean runsOnClient() {
        return this == CLIENT || this == SHARED;
    }
}
