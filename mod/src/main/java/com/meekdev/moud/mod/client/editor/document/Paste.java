package com.meekdev.moud.mod.client.editor.document;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record Paste(String text, InstanceRef parent, List<InstanceRef> roots, List<InstanceRef> all, boolean select, String label,
                    List<Relink> relinks, @Nullable Spot spot) implements Edit {

    public Paste(String text, InstanceRef parent, List<InstanceRef> roots, List<InstanceRef> all, boolean select, String label) {
        this(text, parent, roots, all, select, label, List.of(), null);
    }

    Paste(String text, InstanceRef parent, List<InstanceRef> roots, List<InstanceRef> all, boolean select, String label, List<Relink> relinks) {
        this(text, parent, roots, all, select, label, relinks, null);
    }

    public static Paste fresh(String text, InstanceRef parent, String label) {
        return new Paste(text, parent, new ArrayList<>(), new ArrayList<>(), true, label);
    }

    static Paste fresh(String text, InstanceRef parent, String label, List<Relink> relinks) {
        return new Paste(text, parent, new ArrayList<>(), new ArrayList<>(), true, label, relinks);
    }

    static Paste fresh(String text, InstanceRef parent, String label, List<Relink> relinks, @Nullable Spot spot) {
        return new Paste(text, parent, new ArrayList<>(), new ArrayList<>(), true, label, relinks, spot);
    }

    @Override
    public void apply(SceneDocument document) {
        if (!parent.resolved()) throw new IllegalStateException("still waiting for the server");
        document.paste(this);
        if (!relinks.isEmpty()) document.afterPastes(() -> document.relink(this));
        if (spot != null) document.afterPastes(() -> document.settle(this));
    }

    @Override
    public Edit invert(SceneDocument document) {
        return new Destroy(roots, label);
    }
}
