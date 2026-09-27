package com.meekdev.moud.mod.client.editor.document;

import java.util.ArrayList;
import java.util.List;

public record Paste(String text, InstanceRef parent, List<InstanceRef> roots, List<InstanceRef> all, boolean select, String label,
                    List<Relink> relinks) implements Edit {

    public Paste(String text, InstanceRef parent, List<InstanceRef> roots, List<InstanceRef> all, boolean select, String label) {
        this(text, parent, roots, all, select, label, List.of());
    }

    public static Paste fresh(String text, InstanceRef parent, String label) {
        return new Paste(text, parent, new ArrayList<>(), new ArrayList<>(), true, label);
    }

    static Paste fresh(String text, InstanceRef parent, String label, List<Relink> relinks) {
        return new Paste(text, parent, new ArrayList<>(), new ArrayList<>(), true, label, relinks);
    }

    @Override
    public void apply(SceneDocument document) {
        if (!parent.resolved()) throw new IllegalStateException("still waiting for the server");
        document.paste(this);
        if (!relinks.isEmpty()) document.afterPastes(() -> document.relink(this));
    }

    @Override
    public Edit invert(SceneDocument document) {
        return new Destroy(roots, label);
    }
}
