package com.meekdev.moud.script.host.java;

import com.meekdev.moud.script.host.Builtin;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostObject;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public final class MixinState implements HostObject {

    public record Ref(MixinState state, String key) implements HostObject {

        @Override
        public String typeName() {
            return "MixinStateRef";
        }

        @Override
        public Object get(String key) {
            if (key.equals("value")) return state.value(this.key);
            if (key.equals("key")) return this.key;
            throw new HostError("MixinStateRef has no member '%s'", key);
        }

        @Override
        public void set(String key, Object value) {
            if (!key.equals("value")) throw new HostError("MixinStateRef.%s cannot be assigned", key);
            state.put(this.key, value);
        }
    }

    private static final Object NIL = new Object();

    private final String name;
    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private final Builtin ref = new Builtin("MixinState:ref", a -> new Ref(a.self(MixinState.class), a.string(1)));

    MixinState(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public Object value(String key) {
        Object value = values.get(key);
        return value == NIL ? null : value;
    }

    public void put(String key, Object value) {
        values.put(key, value == null ? NIL : value);
    }

    void fill(Map<String, Object> defaults) {
        for (Map.Entry<String, Object> entry : defaults.entrySet()) values.putIfAbsent(entry.getKey(), entry.getValue() == null ? NIL : entry.getValue());
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new TreeMap<>();
        values.forEach((key, value) -> out.put(key, value == NIL ? null : value));
        return out;
    }

    @Override
    public String typeName() {
        return "MixinState";
    }

    @Override
    public Object get(String key) {
        if (key.equals("ref") && !values.containsKey("ref")) return ref;
        return value(key);
    }

    @Override
    public void set(String key, Object value) {
        put(key, value);
    }
}
