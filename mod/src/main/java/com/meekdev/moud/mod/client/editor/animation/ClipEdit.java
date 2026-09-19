package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.mod.client.editor.document.Edit;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

record ClipEdit(AnimationSession session, Path path, AnimClip target, String label, @Nullable String gesture) implements Edit {

    @Override
    public void apply(SceneDocument document) {
        session.replace(path, target.copy(), label);
    }

    @Override
    public Edit invert(SceneDocument document) {
        AnimClip now = session.clipAt(path);
        if (now == null) throw new IllegalStateException(path.getFileName() + " is not open");
        return new ClipEdit(session, path, now.copy(), label, gesture);
    }
}
