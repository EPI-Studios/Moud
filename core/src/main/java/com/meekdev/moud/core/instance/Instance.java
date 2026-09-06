package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.event.Signal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Instance {

    ClassDef<?> def;
    int id;
    short generation;
    String name = "";
    Instance parent;
    InstanceTree tree;
    long dirty;

    final List<Instance> children = new ArrayList<>(0);

    // the luau proxy, made once and cached so a script always sees the same object
    public Object userdata;

    Signal<PropertyDef> changed;
    Signal<Instance> childAdded;
    Signal<Instance> destroying;

    // only ClassDef.create calls this
    public final void attachClass(ClassDef<?> def) {
        this.def = def;
    }

    public final ClassDef<?> def() { return def; }
    public final int id() { return id; }
    public final short generation() { return generation; }
    public final String name() { return name; }
    public final Instance parent() { return parent; }
    public final InstanceTree tree() { return tree; }
    public final long dirtyMask() { return dirty; }

    public final List<Instance> children() {
        return Collections.unmodifiableList(children);
    }

    public final boolean isAlive() {
        return tree != null;
    }

    public final boolean isA(ClassDef<?> other) {
        return def.isA(other);
    }

    public final Instance child(String name) {
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).name.equals(name)) return children.get(i);
        }
        return null;
    }

    public final Signal<PropertyDef> changed() {
        if (changed == null) changed = new Signal<>();
        return changed;
    }

    public final Signal<Instance> childAdded() {
        if (childAdded == null) childAdded = new Signal<>();
        return childAdded;
    }

    public final Signal<Instance> destroying() {
        if (destroying == null) destroying = new Signal<>();
        return destroying;
    }

    @Override
    public final String toString() {
        return (def == null ? "?" : def.name()) + "#" + id + (name.isEmpty() ? "" : " '" + name + "'");
    }
}
