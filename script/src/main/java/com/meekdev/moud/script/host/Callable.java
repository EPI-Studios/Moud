package com.meekdev.moud.script.host;

public interface Callable {

    Object[] call(Object... args);

    default Callable retain() {
        return this;
    }

    default void release() {}
}
