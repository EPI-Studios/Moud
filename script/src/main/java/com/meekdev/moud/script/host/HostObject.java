package com.meekdev.moud.script.host;

public interface HostObject {

    String typeName();

    Object get(String key);

    default void set(String key, Object value) {
        throw new HostError("%s has no property '%s'", typeName(), key);
    }
}
