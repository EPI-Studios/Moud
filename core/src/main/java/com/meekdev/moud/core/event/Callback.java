package com.meekdev.moud.core.event;

// a question an instance asks whoever answers it, like whether a message reaches this player
//
// a signal tells everyone; a callback asks one place and uses the answer. a place sets it by assigning
// a function, and unset means the engine's own answer
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

    // what the handler returned, or null when nothing answers
    public Object[] call(Object... args) {
        Handler current = handler;
        return current == null ? null : current.call(args);
    }

    // the first thing it returned, or fallback when nothing answers
    public Object first(Object fallback, Object... args) {
        Object[] out = call(args);
        return out == null ? fallback : out.length == 0 ? null : out[0];
    }
}
