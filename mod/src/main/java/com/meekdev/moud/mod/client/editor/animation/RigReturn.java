package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.instance.Model;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;
import org.jspecify.annotations.Nullable;

final class RigReturn {

    record Back(Model model, @Nullable Path clip) {}

    private final Function<Model, IntSupplier> refs;
    private @Nullable Model held;
    private IntSupplier where = () -> 0;
    private @Nullable Path clip;
    private @Nullable String lost;

    RigReturn(Function<Model, IntSupplier> refs) {
        this.refs = refs;
    }

    void seen(@Nullable Model model, @Nullable Path open) {
        if (model != null) {
            if (model != held) where = refs.apply(model);
            held = model;
            clip = open;
            lost = null;
            return;
        }
        if (held == null || held.isAlive()) return;
        lost = Rigging.rigOf(held);
        held = null;
    }

    boolean waiting() {
        return lost != null;
    }

    @Nullable Back back(List<Model> models) {
        String rig = lost;
        if (rig == null) return null;
        Model found = null;
        for (Model model : models) {
            if (model.isAlive() && model.id() == where.getAsInt() && Rigging.rigs(model, rig)) found = model;
        }
        if (found == null) return null;
        lost = null;
        return new Back(found, clip);
    }

    void forget() {
        held = null;
        lost = null;
        clip = null;
    }
}
