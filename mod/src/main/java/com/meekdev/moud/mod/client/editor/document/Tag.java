package com.meekdev.moud.mod.client.editor.document;

public record Tag(InstanceRef target, String tag, boolean added) implements Edit {

    @Override
    public void apply(SceneDocument document) {
        document.tag(target, tag, added);
    }

    @Override
    public Edit invert(SceneDocument document) {
        return new Tag(target, tag, !added);
    }

    @Override
    public String label() {
        return added ? "Add tag " + tag : "Remove tag " + tag;
    }
}
