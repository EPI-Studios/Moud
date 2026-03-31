package com.moud.server.minestom.scripting;

final class ScriptInvocationException extends Exception {
    ScriptInvocationException(String message) {
        super(message);
    }

    ScriptInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
