package com.meekdev.moud.mod.client.editor.plugin;

import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.scene.Scene;
import com.meekdev.moud.mod.client.editor.document.Batch;
import com.meekdev.moud.mod.client.editor.document.Destroy;
import com.meekdev.moud.mod.client.editor.document.Edit;
import com.meekdev.moud.mod.client.editor.document.Paste;
import com.meekdev.moud.mod.client.editor.document.Rename;
import com.meekdev.moud.mod.client.editor.document.Reparent;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.document.SetAttribute;
import com.meekdev.moud.mod.client.editor.document.SetProperty;
import com.meekdev.moud.mod.client.editor.document.Tag;
import com.meekdev.moud.mod.place.Output;
import com.meekdev.moud.script.api.EditsRef;
import com.meekdev.moud.script.host.HostError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class PluginEdits implements EditsRef {

    private final SceneDocument document;
    private final Supplier<String> running;
    private final List<Edit> done = new ArrayList<>();
    private final List<Edit> inverses = new ArrayList<>();
    private final Set<Instance> added = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Instance> chosen = Collections.newSetFromMap(new IdentityHashMap<>());
    private @Nullable String label;
    private int recording;

    PluginEdits(SceneDocument document, Supplier<String> running) {
        this.document = document;
        this.running = running;
    }

    @Override
    public boolean owns(Instance instance) {
        return instance.isAlive() && (instance == document.world() || document.editable(instance));
    }

    boolean pending(Instance instance) {
        for (Instance at = instance; at != null; at = at.parent()) {
            if (added.contains(at)) return true;
        }
        return false;
    }

    void select(Instance instance) {
        chosen.add(instance);
    }

    @Override
    public void write(Instance instance, PropertyDef property, Object value) {
        Object wire = value;
        if (property.type() == PropertyType.INT && value instanceof Number number) wire = number.intValue();
        if (property.type() == PropertyType.REF) {
            if (value instanceof Instance target && !owns(target)) {
                throw new HostError("%s.%s can only point at something in the scene, and %s is not in it yet", instance.name(), property.name(), target.name());
            }
            wire = value instanceof Instance target ? target.id() : null;
        }
        run(new SetProperty(document.ref(instance.id()), property.index(), wire, property.name()));
    }

    @Override
    public void rename(Instance instance, String name) {
        run(new Rename(document.ref(instance.id()), name));
    }

    @Override
    public void reparent(Instance instance, Instance parent) {
        if (!owns(parent)) throw new HostError("%s can only move to somewhere in the scene, and %s is not", instance.name(), parent.name());
        run(new Reparent(document.ref(instance.id()), document.ref(parent.id())));
    }

    @Override
    public void destroy(Instance instance) {
        run(new Destroy(List.of(document.ref(instance.id())), "Delete"));
    }

    @Override
    public void tag(Instance instance, String tag, boolean on) {
        if (instance.hasTag(tag) == on) return;
        run(new Tag(document.ref(instance.id()), tag, on));
    }

    @Override
    public void attribute(Instance instance, String name, Object value) {
        run(new SetAttribute(document.ref(instance.id()), name, value, name));
    }

    @Override
    public void added(Instance instance) {
        if (label == null) label = running.get();
        added.add(instance);
    }

    void record(String name, Runnable changes) {
        if (recording > 0) {
            changes.run();
            return;
        }
        flush();
        recording++;
        label = name;
        try {
            changes.run();
        } finally {
            recording--;
            flush();
        }
    }

    void flush() {
        if (recording > 0) return;
        List<Edit> forward = new ArrayList<>(done);
        List<Edit> backward = new ArrayList<>(inverses.reversed());
        String name = label == null || label.isEmpty() ? "Plugin" : label;
        for (Instance made : List.copyOf(added)) {
            if (!made.isAlive() || made.parent() == null || !owns(made.parent())) continue;
            Instance parent = made.parent();
            String text;
            try {
                text = Scene.save(List.of(made));
            } catch (RuntimeException e) {
                Output.add(Output.Level.WARN, "plugins", "could not add " + made.name() + " to the scene: " + e.getMessage());
                continue;
            }
            Instances.destroy(made);
            Paste paste = new Paste(text, document.ref(parent.id()), new ArrayList<>(), new ArrayList<>(), chosen.contains(made), name);
            try {
                backward.addFirst(paste.invert(document));
                paste.apply(document);
                forward.add(paste);
            } catch (RuntimeException e) {
                backward.removeFirst();
                Output.add(Output.Level.WARN, "plugins", "could not add " + made.name() + " to the scene: " + e.getMessage());
            }
        }
        added.removeIf(made -> !made.isAlive());
        chosen.clear();
        done.clear();
        inverses.clear();
        label = null;
        if (forward.isEmpty()) return;
        document.history().record(new Batch(name, forward), new Batch(name, backward));
    }

    private void run(Edit edit) {
        Edit inverse;
        try {
            inverse = edit.invert(document);
            edit.apply(document);
        } catch (RuntimeException e) {
            throw new HostError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        if (label == null) label = running.get();
        done.add(edit);
        inverses.add(inverse);
    }
}
