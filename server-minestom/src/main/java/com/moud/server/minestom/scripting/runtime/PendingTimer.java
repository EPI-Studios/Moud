package com.moud.server.minestom.scripting.runtime;


import com.moud.server.minestom.scripting.ScriptCallable;

public final class PendingTimer {
    double timeLeft;
    final ScriptCallable callback;

    public PendingTimer(double timeLeft, ScriptCallable callback) {
        this.timeLeft = timeLeft;
        this.callback = callback;
    }
}
