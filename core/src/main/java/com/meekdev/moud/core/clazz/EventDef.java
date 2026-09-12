package com.meekdev.moud.core.clazz;

import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import java.lang.reflect.Field;

// something a class tells you about
//
// most carry the instance it happened to and a handler that wants more reads it off that, which keeps
// one shape for nearly every event. a channel is the exception and has to be: what arrived was sent by
// somebody, and there is no instance to read it off
public record EventDef(String name, Field field) {

    @SuppressWarnings("unchecked")
    // whatever the class said it carries, which is not always an instance: the three built in events
    // hand over an instance or a property, and a channel hands over a delivery. the binding decides
    // how to push what it gets, and that is the only place that can know
    public Signal<Object> on(Instance instance) {
        try {
            return (Signal<Object>) field.get(instance);
        } catch (IllegalAccessException unreachable) {
            throw new IllegalStateException("cannot reach " + name + " on " + instance, unreachable);
        }
    }
}
