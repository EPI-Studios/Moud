package com.meekdev.moud.mod.client.editor.document;

public record SetProperty(InstanceRef target, int property, Object value, String label) implements Edit {

    @Override
    public void apply(SceneDocument document) {
        document.write(target, property, value);
    }

    @Override
    public Edit invert(SceneDocument document) {
        return new SetProperty(target, property, document.read(target, property), label);
    }

    @Override
    public String gesture() {
        return target.id() + ":" + property;
    }
}
