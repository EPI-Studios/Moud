package com.meekdev.moud.addon.java;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.engine.ScriptEngine;
import com.meekdev.moud.script.err.ScriptError;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Fiber;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.ScriptValue;
import com.meekdev.moud.script.host.ThreadFiber;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;

final class JavaEngine implements ScriptEngine {

    private record Table(Map<String, Object> data) implements ScriptValue {}

    private final Host host;
    private final Compiler compiler = new Compiler();

    JavaEngine(Host host) {
        this.host = host;
    }

    private PlaceScript instantiate(String chunk, String source, Instance script) {
        Class<?> type = compiler.compile(chunk, source);
        if (!PlaceScript.class.isAssignableFrom(type)) {
            throw new ScriptError(chunk, type.getSimpleName() + " has to extend PlaceScript", null);
        }
        try {
            PlaceScript place = (PlaceScript) type.getDeclaredConstructor().newInstance();
            place.attach(host, script);
            return place;
        } catch (InvocationTargetException e) {
            throw new ScriptError(chunk, String.valueOf(e.getCause()), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new ScriptError(chunk, type.getSimpleName() + " needs a public constructor that takes nothing", e);
        }
    }

    @Override
    public void run(String chunk, String source) {
        instantiate(chunk, source, null).run();
    }

    @Override
    public Object module(String chunk, String source) {
        PlaceScript module = instantiate(chunk, source, null);
        module.run();
        return module;
    }

    @Override
    public Fiber fiber(Callable fn) {
        return new ThreadFiber("moud-java-task", fn::call);
    }

    @Override
    public Fiber script(String chunk, String source, Instance script) {
        PlaceScript place = instantiate(chunk, source, script);
        return new ThreadFiber("moud-java-" + chunk, args -> {
            place.run();
            return null;
        });
    }

    @Override
    public ScriptValue table(Map<String, Object> data) {
        return new Table(new LinkedHashMap<>(data));
    }

    @Override
    public Map<String, Object> read(ScriptValue table) {
        return table instanceof Table t ? t.data() : Map.of();
    }

    @Override
    public void close() {}
}
