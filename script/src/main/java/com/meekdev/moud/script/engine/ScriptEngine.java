package com.meekdev.moud.script.engine;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Fiber;
import com.meekdev.moud.script.host.ScriptValue;
import java.util.Map;

public interface ScriptEngine extends AutoCloseable {

    void run(String chunk, String source);

    Object module(String chunk, String source);

    Fiber fiber(Callable fn);

    Fiber script(String chunk, String source, Instance script);

    ScriptValue table(Map<String, Object> data);

    Map<String, Object> read(ScriptValue table);

    @Override
    void close();
}
