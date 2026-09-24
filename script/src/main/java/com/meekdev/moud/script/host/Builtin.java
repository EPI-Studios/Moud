package com.meekdev.moud.script.host;

public record Builtin(String name, HostFunction body, boolean parallel) {

    public Builtin(String name, HostFunction body) {
        this(name, body, false);
    }

    public Builtin inParallel() {
        return parallel ? this : new Builtin(name, body, true);
    }
}
