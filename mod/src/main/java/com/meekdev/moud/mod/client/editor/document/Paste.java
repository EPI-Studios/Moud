package com.meekdev.moud.mod.client.editor.document;

import java.util.ArrayList;
import java.util.List;

public record Paste(String text, InstanceRef parent, List<InstanceRef> roots, List<InstanceRef> all, boolean select, String label) implements Edit {

    public static Paste fresh(String text, InstanceRef parent, String label) {
        return new Paste(text, parent, new ArrayList<>(), new ArrayList<>(), true, label);
    }

    @Override
    public void apply(SceneDocument document) {
        if (!parent.resolved()) throw new IllegalStateException("still waiting for the server");
        document.paste(this);
    }

    @Override
    public Edit invert(SceneDocument document) {
        return new Destroy(roots, label);
    }
}
