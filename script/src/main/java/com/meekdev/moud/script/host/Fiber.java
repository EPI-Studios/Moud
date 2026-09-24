package com.meekdev.moud.script.host;

import com.meekdev.moud.script.engine.ScriptEngine;

public interface Fiber {

    Suspend resume(Object... values);

    void cancel();

    default Object[] results() {
        return new Object[0];
    }

    default ScriptEngine engine() {
        return null;
    }
}
