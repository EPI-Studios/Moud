package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Assets;
import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import java.util.LinkedHashSet;
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

    // the initialiser is the one place a field may be written directly, the instance is not in the tree yet
    public static <T extends Instance> T create(ClassDef<T> def, Instance parent, String name, Consumer<T> init) {
        return create(def, parent, name, false, init);
    }

    // the one way a mirror makes an instance: the id comes from the authority, not from a counter
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

    // local instances get negative ids so "is this replicated" is a sign test
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

        // depth first so a handler never sees a child whose parent is already gone
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
        if (i.tags.add(tag)) i.tree.tag(i, tag, true);
    }

    public static void removeTag(Instance i, String tag) {
        checkTag(i, tag);
        if (i.tags != null && i.tags.remove(tag)) i.tree.tag(i, tag, false);
    }

    private static void checkTag(Instance i, String tag) {
        if (i.tree == null) throw new IllegalStateException(i + " is destroyed");
        if (tag == null || tag.isBlank()) throw new IllegalArgumentException("a tag is some text, not nothing");
    }

    public static void rename(Instance i, String name) {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("name cannot be empty");
        i.name = name;
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
        Object current = p.getObj(i);
        if (current == null ? value == null : current.equals(value)) return;
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
        if (i.dirty == 0) i.tree.markDirty(i);
        i.dirty |= 1L << p.index();
        if (i.changed != null) i.changed.fire(p);
    }

    private static boolean isAncestor(Instance maybeAncestor, Instance of) {
        for (Instance c = of.parent; c != null; c = c.parent) {
            if (c == maybeAncestor) return true;
        }
        return false;
    }
}
