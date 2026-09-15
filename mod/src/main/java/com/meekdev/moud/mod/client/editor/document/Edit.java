package com.meekdev.moud.mod.client.editor.document;

import org.jspecify.annotations.Nullable;

public interface Edit {

    void apply(SceneDocument document);

    Edit invert(SceneDocument document);

    String label();

    default @Nullable String gesture() {
        return null;
    }
}
