package com.meekdev.moud.script.host.java;

import com.meekdev.moud.script.host.Builtin;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostObject;
import java.lang.reflect.Field;

public record JavaField(Field field) implements HostObject {

    private static final Builtin CHANGED = new Builtin("JavaField:changed", Mixins::field);

    record Fields(Class<?> type) implements HostObject {

        @Override
        public String typeName() {
            return "JavaFields";
        }

        @Override
        public Object get(String key) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                try {
                    return new JavaField(c.getDeclaredField(key));
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw new HostError("%s has no field '%s'", type.getName(), key);
        }
    }

    @Override
    public String typeName() {
        return "JavaField";
    }

    @Override
    public Object get(String key) {
        return switch (key) {
            case "name" -> field.getName();
            case "type" -> field.getType().getName();
            case "changed" -> CHANGED;
            default -> throw new HostError("JavaField has no member '%s'", key);
        };
    }
}
