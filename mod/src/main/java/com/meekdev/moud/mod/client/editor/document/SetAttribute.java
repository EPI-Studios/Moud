package com.meekdev.moud.mod.client.editor.document;

import org.jspecify.annotations.Nullable;

public record SetAttribute(InstanceRef target, String name, @Nullable Object value, String label) implements Edit {

    @Override
    public void apply(SceneDocument document) {
        document.attribute(target, name, value);
    }

    @Override
    public Edit invert(SceneDocument document) {
        return new SetAttribute(target, name, document.attribute(target, name), label);
    }

    @Override
    public String gesture() {
        return target.id() + ":@" + name;
    }
}
