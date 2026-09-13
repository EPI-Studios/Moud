package com.meekdev.moud.core.clazz;

import com.meekdev.moud.core.event.Callback;
import com.meekdev.moud.core.instance.Instance;
import java.lang.reflect.Field;

// a question a class asks a place, declared as a public final Callback field
public record CallbackDef(String name, Field field) {

    public Callback on(Instance instance) {
        try {
            return (Callback) field.get(instance);
        } catch (IllegalAccessException unreachable) {
            throw new IllegalStateException("cannot reach " + name + " on " + instance, unreachable);
        }
    }
}
