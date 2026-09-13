package com.meekdev.moud.core.clazz;

import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import java.lang.reflect.Field;

public record EventDef(String name, Field field) {

    @SuppressWarnings("unchecked")
    public Signal<Object> on(Instance instance) {
        try {
            return (Signal<Object>) field.get(instance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot reach " + name + " on " + instance, e);
        }
    }
}
