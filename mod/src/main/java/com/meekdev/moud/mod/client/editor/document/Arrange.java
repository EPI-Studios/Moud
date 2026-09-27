package com.meekdev.moud.mod.client.editor.document;

import java.util.List;

public record Arrange(InstanceRef parent, List<InstanceRef> children, String label) implements Edit {

    @Override
    public void apply(SceneDocument document) {
        document.arrange(parent, children);
    }

    @Override
    public Edit invert(SceneDocument document) {
        return new Arrange(parent, document.childRefs(parent), label);
    }
}
