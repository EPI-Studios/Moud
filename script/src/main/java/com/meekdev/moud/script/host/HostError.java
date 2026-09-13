package com.meekdev.moud.script.host;

public final class HostError extends RuntimeException {

    public HostError(String message) {
        super(message, null, false, false);
    }

    public HostError(String format, Object... args) {
        this(String.format(format, args));
    }
}
