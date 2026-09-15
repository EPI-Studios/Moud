package com.meekdev.moud.mod.client.editor.document;

import com.meekdev.moud.core.instance.Instance;

public record Rename(InstanceRef target, String name) implements Edit {

    @Override
    public void apply(SceneDocument document) {
        document.rename(target, name);
    }

    @Override
    public Edit invert(SceneDocument document) {
        Instance instance = document.find(target);
        if (instance == null) throw new IllegalStateException("that instance is gone");
        return new Rename(target, instance.name());
    }

    @Override
    public String label() {
        return "Rename";
    }
}
