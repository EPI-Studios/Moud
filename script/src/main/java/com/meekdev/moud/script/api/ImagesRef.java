package com.meekdev.moud.script.api;

import com.meekdev.moud.core.image.EditableImage;
import com.meekdev.moud.core.image.GlyphFont;
import java.util.concurrent.CompletableFuture;

public interface ImagesRef {

    CompletableFuture<EditableImage> load(String source);

    default CompletableFuture<EditableImage> skin(String player, boolean head) {
        return CompletableFuture.failedFuture(new IllegalStateException("skins are read in a LocalScript"));
    }

    default GlyphFont font() {
        return null;
    }
}
