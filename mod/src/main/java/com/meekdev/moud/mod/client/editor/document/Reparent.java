package com.meekdev.moud.mod.client.editor.document;

import com.meekdev.moud.core.instance.Instance;

public record Reparent(int id, int parent) implements Edit {

    @Override
    public void apply(SceneDocument document) {
        document.reparent(id, parent);
    }

    @Override
    public Edit invert(SceneDocument document) {
        Instance instance = document.find(id);
        if (instance == null || instance.parent() == null) throw new IllegalStateException("that instance is gone");
        return new Reparent(id, instance.parent().id());
    }

    @Override
    public String label() {
        return "Move";
    }
}
