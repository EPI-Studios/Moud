package com.meekdev.moud.mod.client.editor.document;

public record SetProperty(int id, int property, Object value, String label) implements Edit {

    @Override
    public void apply(SceneDocument document) {
        document.write(id, property, value);
    }

    @Override
    public Edit invert(SceneDocument document) {
        return new SetProperty(id, property, document.read(id, property), label);
    }

    @Override
    public String gesture() {
        return id + ":" + property;
    }
}
