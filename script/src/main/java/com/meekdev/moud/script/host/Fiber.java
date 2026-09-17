package com.meekdev.moud.script.host;

public interface Fiber {

    Suspend resume(Object... values);

    void cancel();

    default Object[] results() {
        return new Object[0];
    }
}
