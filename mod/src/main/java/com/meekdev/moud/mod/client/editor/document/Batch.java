package com.meekdev.moud.mod.client.editor.document;

import java.util.ArrayList;
import java.util.List;

public record Batch(String label, List<Edit> edits) implements Edit {

    @Override
    public void apply(SceneDocument document) {
        for (Edit edit : edits) edit.apply(document);
    }

    @Override
    public Edit invert(SceneDocument document) {
        List<Edit> inverse = new ArrayList<>(edits.size());
        for (Edit edit : edits) inverse.addFirst(edit.invert(document));
        return new Batch(label, inverse);
    }

    @Override
    public String gesture() {
        StringBuilder key = new StringBuilder();
        for (Edit edit : edits) {
            if (edit.gesture() == null) return null;
            key.append(edit.gesture()).append(';');
        }
        return key.toString();
    }
}
