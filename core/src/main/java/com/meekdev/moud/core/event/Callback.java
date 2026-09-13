package com.meekdev.moud.core.event;

public final class Callback {

    @FunctionalInterface
    public interface Handler {
        Object[] call(Object... args);
    }

    private Handler handler;

    public void set(Handler handler) {
        this.handler = handler;
    }

    public boolean isSet() {
        return handler != null;
    }

    public Object[] call(Object... args) {
        Handler current = handler;
        return current == null ? null : current.call(args);
    }

    public Object first(Object fallback, Object... args) {
        Object[] out = call(args);
        return out == null ? fallback : out.length == 0 ? null : out[0];
    }
}
