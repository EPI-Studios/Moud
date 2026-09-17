package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Assets;
import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.function.Consumer;

public final class Instances {

    private Instances() {}

    public static <T extends Instance> T createRoot(InstanceTree tree, ClassDef<T> def, String name) {
        if (tree.root() != null) throw new IllegalStateException("tree already has a root");
        T root = def.create();
        root.id = tree.nextId++;
        root.name = name;
        root.tree = tree;
        tree.setRoot(root);
        tree.index(root);
        return root;
    }

    public static <T extends Instance> T create(ClassDef<T> def, Instance parent, String name) {
        return create(def, parent, name, false, null);
    }

    public static <T extends Instance> T create(ClassDef<T> def, Instance parent, String name, Consumer<T> init) {
        return create(def, parent, name, false, init);
    }

    public static <T extends Instance> T adopt(ClassDef<T> def, Instance parent, int id, String name) {
        T i = def.create();
        i.id = id;
        i.name = name;
        i.tree = parent.tree;
        i.parent = parent;
        parent.children.add(i);
        parent.tree.index(i);
        if (parent.childAdded != null) parent.childAdded.fire(i);
        return i;
    }

    public static <T extends Instance> T createLocal(ClassDef<T> def, Instance parent, String name) {
        return create(def, parent, name, true, null);
    }

    private static <T extends Instance> T create(ClassDef<T> def, Instance parent, String name,
                                                 boolean local, Consumer<T> init) {
        if (parent == null) throw new IllegalArgumentException("parent is required, use createRoot");
        InstanceTree tree = parent.tree;
        if (tree == null) throw new IllegalStateException("parent " + parent + " is not in a tree");

        T i = def.create();
        i.id = local || tree.mirror ? tree.nextLocalId-- : tree.nextId++;
        i.name = name == null ? def.name() : name;
        if (init != null) init.accept(i);
        i.tree = tree;
        i.parent = parent;
        parent.children.add(i);
        tree.index(i);

        i.build();
        if (parent.childAdded != null) parent.childAdded.fire(i);
        return i;
    }

    public static void destroy(Instance i) {
        if (i.tree == null) return;
        if (i.tree.root() == i) throw new IllegalArgumentException("cannot destroy the root");

        for (int n = i.children.size() - 1; n >= 0; n--) destroy(i.children.get(n));

        if (i.destroying != null) i.destroying.fire(i);
        if (i.parent != null) i.parent.children.remove(i);
        i.tree.unindex(i);
        i.parent = null;
        i.tree = null;
        i.dirty = 0;
        i.generation++;
        i.userdata = null;
    }

    public static void reparent(Instance i, Instance newParent) {
        if (i.tree == null) throw new IllegalStateException(i + " is destroyed");
        if (newParent == null) throw new IllegalArgumentException("use destroy to detach");
        if (newParent.tree != i.tree) throw new IllegalArgumentException("cannot move between trees");
        if (i == i.tree.root()) throw new IllegalArgumentException("cannot reparent the root");
        if (i == newParent || isAncestor(i, newParent)) {
            throw new IllegalArgumentException("reparenting " + i + " under " + newParent + " makes a cycle");
        }
        if (i.parent == newParent) return;

        i.parent.children.remove(i);
        i.parent = newParent;
        newParent.children.add(i);
        i.tree.structureEpoch++;
        i.tree.markMoved(i);
        if (newParent.childAdded != null) newParent.childAdded.fire(i);
    }

    public static void addTag(Instance i, String tag) {
        checkTag(i, tag);
        if (i.tags == null) i.tags = new LinkedHashSet<>();
        if (i.tags.add(tag)) {
            i.tree.mutations++;
            i.tree.tag(i, tag, true);
        }
    }

    public static void removeTag(Instance i, String tag) {
        checkTag(i, tag);
        if (i.tags != null && i.tags.remove(tag)) {
            i.tree.mutations++;
            i.tree.tag(i, tag, false);
        }
    }

    public static void setAttribute(Instance i, String name, Object value) {
        if (i.tree == null) throw new IllegalStateException(i + " is destroyed");
        Attributes.checkName(name);
        Object next = Attributes.normalize(value);
        if (Objects.equals(i.attribute(name), next)) return;
        if (next == null) {
            i.attributes.remove(name);
        } else {
            if (i.attributes == null) i.attributes = new LinkedHashMap<>();
            i.attributes.put(name, next);
        }
        i.tree.mutations++;
        i.tree.attributed(i, name);
        if (i.attributeChanged != null) i.attributeChanged.fire(name);
    }

    private static void checkTag(Instance i, String tag) {
        if (i.tree == null) throw new IllegalStateException(i + " is destroyed");
        if (tag == null || tag.isBlank()) throw new IllegalArgumentException("tag cannot be empty");
    }

    public static void rename(Instance i, String name) {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("name cannot be empty");
        if (name.equals(i.name)) return;
        i.name = name;
        if (i.tree != null) {
            i.tree.markRenamed(i);
            i.tree.mutations++;
        }
    }

    public static void setNum(Instance i, PropertyDef p, double value) {
        expect(i, p, p.isNumeric());
        double clamped = p.clamp(value);
        if (p.getNum(i) == clamped) return;
        p.writeNum(i, clamped);
        touch(i, p);
    }

    public static void setBool(Instance i, PropertyDef p, boolean value) {
        expect(i, p, p.type() == PropertyType.BOOL);
        if (p.getBool(i) == value) return;
        p.writeBool(i, value);
        touch(i, p);
    }

    public static void setObj(Instance i, PropertyDef p, Object value) {
        expect(i, p, !p.isNumeric() && p.type() != PropertyType.BOOL);
        if (p.asset() && value instanceof String text) {
            Assets.check(i.def().name() + "." + p.name(), text);
        }
        if (Objects.equals(p.getObj(i), value)) return;
        p.writeObj(i, value);
        touch(i, p);
    }

    private static void expect(Instance i, PropertyDef p, boolean ok) {
        if (i.tree == null) throw new IllegalStateException(i + " is destroyed");
        if (i.def().property(p.index()) != p) {
            throw new IllegalArgumentException(p.name() + " does not belong to " + i.def());
        }
        if (!ok) throw new IllegalArgumentException(p.name() + " is a " + p.type() + ", wrong setter");
    }

    private static void touch(Instance i, PropertyDef p) {
        if (i instanceof Spatial && moves(p)) i.tree.spatialTouched.add(i);
        i.tree.mutations++;
        if (i.dirty == 0) i.tree.markDirty(i);
        i.dirty |= 1L << p.index();
        if (i.changed != null) i.changed.fire(p);
    }

    private static boolean moves(PropertyDef p) {
        String name = p.name();
        return name.equals("cframe") || name.equals("size") || name.equals("pivot");
    }

    private static boolean isAncestor(Instance maybeAncestor, Instance of) {
        for (Instance c = of.parent; c != null; c = c.parent) {
            if (c == maybeAncestor) return true;
        }
        return false;
    }
}
