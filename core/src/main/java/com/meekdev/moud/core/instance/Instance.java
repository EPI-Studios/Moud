package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.event.Signal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class Instance {

    ClassDef<?> def;
    int id;
    short generation;
    String name = "";
    Instance parent;
    InstanceTree tree;
    long dirty;

    final List<Instance> children = new ArrayList<>(0);

    public Object userdata;

    Set<String> tags;

    Map<String, Object> attributes;

    Signal<PropertyDef> changed;
    Signal<Instance> childAdded;
    Signal<Instance> destroying;
    Signal<Instance> renamed;
    Signal<String> attributeChanged;
    Signal<Instance> ancestryChanged;
    Signal<Instance> descendantAdded;
    Signal<Instance> descendantRemoving;

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

    public final Set<String> tags() {
        return tags == null ? Set.of() : Collections.unmodifiableSet(tags);
    }

    public final boolean hasTag(String tag) {
        return tags != null && tags.contains(tag);
    }

    public final Map<String, Object> attributes() {
        return attributes == null ? Map.of() : Collections.unmodifiableMap(attributes);
    }

    public final Object attribute(String name) {
        return attributes == null ? null : attributes.get(name);
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

    public final Signal<Instance> renamed() {
        if (renamed == null) renamed = new Signal<>();
        return renamed;
    }

    public final Signal<String> attributeChanged() {
        if (attributeChanged == null) attributeChanged = new Signal<>();
        return attributeChanged;
    }

    public final Signal<Instance> ancestryChanged() {
        if (ancestryChanged == null) ancestryChanged = listened(new Signal<>());
        return ancestryChanged;
    }

    public final Signal<Instance> descendantAdded() {
        if (descendantAdded == null) descendantAdded = listened(new Signal<>());
        return descendantAdded;
    }

    public final Signal<Instance> descendantRemoving() {
        if (descendantRemoving == null) descendantRemoving = listened(new Signal<>());
        return descendantRemoving;
    }

    private Signal<Instance> listened(Signal<Instance> signal) {
        if (tree != null) tree.hierarchyListened = true;
        return signal;
    }

    @Override
    public final String toString() {
        return (def == null ? "?" : def.name()) + "#" + id + (name.isEmpty() ? "" : " '" + name + "'");
    }

    protected void build() {
    }

    public boolean serverOnly() {
        return false;
    }

    public boolean holdsTemplates() {
        return false;
    }

    public boolean storesAway() {
        return false;
    }

    public static boolean dormant(Instance instance) {
        for (Instance at = instance.parent(); at != null; at = at.parent()) {
            if (at.holdsTemplates()) return true;
        }
        return false;
    }

    public static boolean outOfWorld(Instance instance) {
        return container(instance) != null;
    }

    public static Instance container(Instance instance) {
        for (Instance at = instance.parent(); at != null; at = at.parent()) {
            if (at.holdsTemplates() || at.serverOnly() || at.storesAway()) return at;
        }
        return null;
    }

    public static boolean hidden(Instance instance) {
        for (Instance at = instance; at != null; at = at.parent()) {
            if (at.serverOnly()) return true;
        }
        return false;
    }

    protected long externalPropertyMask() {
        return def().driven();
    }

    public final long externalProperties() {
        return externalPropertyMask();
    }

    protected void shape() {
    }

    protected void drive(double dt) {
    }

    protected void evaluate() {
    }

    protected void simulate(double dt) {
    }

    protected void compose() {
    }
}
