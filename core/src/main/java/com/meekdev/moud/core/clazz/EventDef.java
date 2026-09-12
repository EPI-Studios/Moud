package com.meekdev.moud.core.clazz;

import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import java.lang.reflect.Field;

// something a class tells you about
//
// every one carries the instance it happened to and nothing else. a handler that wants more reads
// it off that -- which keeps one shape for every event rather than a payload per signal that the
// binding would have to know about one at a time
public record EventDef(String name, Field field) {

    @SuppressWarnings("unchecked")
    public Signal<Instance> on(Instance instance) {
        try {
            return (Signal<Instance>) field.get(instance);
        } catch (IllegalAccessException unreachable) {
            throw new IllegalStateException("cannot reach " + name + " on " + instance, unreachable);
        }
    }
}
