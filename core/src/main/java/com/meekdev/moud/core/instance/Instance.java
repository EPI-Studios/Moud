package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.event.Signal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    // whatever the script layer hangs off this instance, cleared when it dies. core never reads it
    public Object userdata;

    // the labels a place hung on it, which replicate like its properties. null until the first one
    Set<String> tags;

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

    public final Set<String> tags() {
        return tags == null ? Set.of() : Collections.unmodifiableSet(tags);
    }

    public final boolean hasTag(String tag) {
        return tags != null && tags.contains(tag);
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

    // a class that is made of more than itself builds the rest here, once it is in the tree and
    // can have children. the mirror never calls this: what a replicated instance is made of
    // arrives over the wire, and building it twice would give it two of everything
    protected void build() {
    }

    // which of this instance's properties the other side already has by some other route
    //
    // sending one of these is sending it twice, and the copy we would send is the one with the wrong
    // guarantee: a property delta is reliable and ordered, which for something that changes every tick
    // means a lost packet delays every later one until it is retransmitted -- the newest sample
    // arriving late is exactly what a stream of samples does not want
    //
    // a mask rather than a flag per property, because it is one question asked once per instance per
    // tick. zero, for almost everything: a colour reaches the other side one way only
    //
    // the class named which properties on the fields themselves. a class overrides this when the
    // answer is conditional -- a body's state comes off its player only while a player is wearing it
    protected long propertiesFromElsewhere() {
        return def().driven();
    }

    public final long fromElsewhere() {
        return propertiesFromElsewhere();
    }

    // the per tick stages, in Stage order. a class overrides the ones that concern it and ClassDef
    // notices, so nothing here is dispatched on a type test and nothing has to be registered
    //
    // the one rule every one of these obeys: write only what this stage owns. where the engine has
    // to compute something a place also wants hold of, they are two properties and not one -- c0 is
    // the shoulder line and transform is the turn at it. a stage that re-asserts a whole body from
    // a table it keeps is the bug the split exists to forbid, because a place can then never take a
    // limb away, resize one, or hold a pose of its own

    // geometry out of parameters
    protected void shape() {
    }

    // where this is going and what state that puts it in
    protected void drive(double dt) {
    }

    // what poses this, blended
    protected void evaluate() {
    }

    // what physics poses instead
    protected void simulate(double dt) {
    }

    // the frames that come out of the above
    protected void compose() {
    }
}
