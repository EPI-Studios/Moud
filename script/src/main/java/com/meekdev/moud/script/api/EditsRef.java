package com.meekdev.moud.script.api;

import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;

public interface EditsRef {

    boolean owns(Instance instance);

    void write(Instance instance, PropertyDef property, Object value);

    void rename(Instance instance, String name);

    void reparent(Instance instance, Instance parent);

    void destroy(Instance instance);

    void tag(Instance instance, String tag, boolean added);

    void attribute(Instance instance, String name, Object value);

    void added(Instance instance);
}
