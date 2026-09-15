package com.meekdev.moud.mod.client.editor.document;

import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.place.Place;
import com.meekdev.moud.net.replicate.Applier;
import com.meekdev.moud.net.replicate.Change;
import com.meekdev.moud.net.wire.Codec;
import java.util.List;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

public final class SceneDocument {

    private final Selection selection = new Selection();
    private final History history = new History(this);
    private @Nullable InstanceTree seen;

    public Selection selection() {
        return selection;
    }

    public History history() {
        return history;
    }

    public @Nullable InstanceTree tree() {
        return ClientScene.tree();
    }

    public @Nullable Instance world() {
        return ClientScene.world();
    }

    public @Nullable Instance find(int id) {
        InstanceTree tree = tree();
        Instance world = world();
        if (tree == null || world == null) return null;
        if (id == world.id()) return world;
        Instance found = tree.byId(id);
        return found != null && found.isAlive() ? found : null;
    }

    public @Nullable Instance primary() {
        OptionalInt id = selection.primary();
        return id.isPresent() ? find(id.getAsInt()) : null;
    }

    public boolean editable(@Nullable Instance instance) {
        return instance != null && instance != world() && instance.isAlive() && Place.authored(instance);
    }

    public boolean dirty() {
        return SceneLink.dirty();
    }

    public void frame(boolean gestureHeld) {
        InstanceTree tree = tree();
        if (tree != seen) {
            seen = tree;
            selection.clear();
            history.clear();
        }
        selection.keep(id -> find(id) != null);
        history.settle(gestureHeld);
        int inserted = SceneLink.takeInserted();
        if (inserted != 0) selection.select(inserted);
    }

    public Object read(int id, int property) {
        Instance instance = require(id);
        PropertyDef def = instance.def().property(property);
        if (def == null) throw new IllegalStateException("no such property");
        return wire(instance, def);
    }

    public static @Nullable Object wire(Instance instance, PropertyDef property) {
        if (property.type().isBool()) return property.getBool(instance);
        if (property.type() == PropertyType.INT) return (int) property.getNum(instance);
        if (property.type() == PropertyType.NUM) return property.getNum(instance);
        if (property.type() == PropertyType.REF) return property.getObj(instance) instanceof Instance target ? target.id() : null;
        return property.getObj(instance);
    }

    void write(int id, int property, @Nullable Object value) {
        editableOrThrow(id);
        apply(new Change.Wrote(id, property, value));
    }

    void reparent(int id, int parent) {
        editableOrThrow(id);
        Instance target = find(parent);
        if (target == null || target != world() && !editable(target)) throw new IllegalStateException("the new parent is not part of the scene");
        apply(new Change.Moved(id, parent));
    }

    public void destroy(List<Integer> ids) {
        for (int id : ids) {
            if (!editable(find(id))) continue;
            apply(new Change.Destroyed(id));
        }
    }

    public void insert(String className, int parent) {
        SceneLink.insert(className, parent);
    }

    public void save() {
        SceneLink.save();
    }

    private void apply(Change change) {
        InstanceTree tree = tree();
        Instance world = world();
        if (tree == null || world == null) throw new IllegalStateException("there is no scene");
        byte[] bytes = Codec.encode(List.of(change), tree, Addons.classes());
        Applier applier = new Applier(Addons.classes(), tree, world);
        for (Change normalized : Codec.decode(bytes, tree, Addons.classes())) {
            applier.apply(normalized);
            if (normalized instanceof Change.Wrote wrote) {
                Instance instance = find(wrote.id());
                PropertyDef property = instance == null ? null : instance.def().property(wrote.property());
                if (property != null) PendingEdits.sent(wrote.id(), wrote.property(), wire(instance, property));
            }
        }
        SceneLink.send(bytes);
    }

    private Instance require(int id) {
        Instance instance = find(id);
        if (instance == null) throw new IllegalStateException("that instance is gone");
        return instance;
    }

    private void editableOrThrow(int id) {
        if (!editable(find(id))) throw new IllegalStateException("that instance is not part of the scene");
    }
}
