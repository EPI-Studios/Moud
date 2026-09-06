package com.meekdev.moud.script.err;

public final class ScriptError extends RuntimeException {

    private final String chunk;

    public ScriptError(String chunk, String message, Throwable cause) {
        super(chunk + ": " + message, cause);
        this.chunk = chunk;
    }

    public String chunk() {
        return chunk;
    }
}
