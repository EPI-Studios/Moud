package com.moud.server.minestom.scripting;

public final class ScriptInvocationException extends Exception {
    public ScriptInvocationException(String message) {
        super(message);
    }

    public ScriptInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
