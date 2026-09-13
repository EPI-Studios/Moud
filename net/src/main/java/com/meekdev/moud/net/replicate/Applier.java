package com.meekdev.moud.net.replicate;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;

// builds the mirror from a change stream. it never invents an id, so both sides agree on identity
public final class Applier {

    private final ClassRegistry classes;
    private InstanceTree tree;
    private Instance world;

    public Applier(ClassRegistry classes) {
        this.classes = classes;
    }

    public InstanceTree tree() {
        return tree;
    }

    public Instance world() {
        return world;
    }

    public void apply(Change change) {
        switch (change) {
            case Change.Reset ignored -> {
                tree = new InstanceTree(true);
                world = Instances.createRoot(tree, Classes.SPATIAL, "World");
            }
            case Change.Created created -> create(created);
            case Change.Wrote wrote -> write(wrote);
            case Change.Moved moved -> move(moved);
            case Change.Destroyed destroyed -> {
                Instance instance = tree == null ? null : tree.byId(destroyed.id());
                if (instance != null) Instances.destroy(instance);
            }
        }
    }

    private void create(Change.Created created) {
        if (tree == null) return;
        Instance parent = created.parent() == world.id() ? world : tree.byId(created.parent());
        if (parent == null || tree.byId(created.id()) != null) return;
        ClassDef<?> def = classes.require(created.className());
        Instances.adopt(def, parent, created.id(), created.name());
    }

    private void move(Change.Moved moved) {
        if (tree == null) return;
        Instance instance = tree.byId(moved.id());
        Instance parent = moved.parent() == world.id() ? world : tree.byId(moved.parent());
        if (instance == null || parent == null || instance.parent() == parent) return;
        Instances.reparent(instance, parent);
    }

    private void write(Change.Wrote wrote) {
        if (tree == null) return;
        Instance instance = tree.byId(wrote.id());
        if (instance == null) return;
        PropertyDef property = instance.def().property(wrote.property());
        if (property == null) return;
        // resolved in this tree, by the id the authority sent
        //
        // a reference forward to something not created yet lands as nothing. the authority emits
        // every creation before any write, so that only happens for a target outside the tree --
        // a local instance, which could never have crossed anyway
        if (property.type() == PropertyType.REF) {
            Object id = wrote.value();
            Instances.setObj(instance, property,
                    id instanceof Integer at ? tree.byId(at) : null);
            return;
        }
        // by the property's type rather than the value's: an int comes off the wire as an Integer
        // and a number held in memory as a Double, and both are numbers
        if (property.isNumeric()) {
            if (wrote.value() instanceof Number n) Instances.setNum(instance, property, n.doubleValue());
            return;
        }
        switch (wrote.value()) {
            case Boolean b -> Instances.setBool(instance, property, b);
            case null -> { }
            default -> Instances.setObj(instance, property, wrote.value());
        }
    }
}
