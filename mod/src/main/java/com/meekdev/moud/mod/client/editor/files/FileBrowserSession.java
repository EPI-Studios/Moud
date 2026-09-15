package com.meekdev.moud.mod.client.editor.files;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

record FileBrowserSession(String title, boolean foldersOnly, Set<String> extensions,
                          Consumer<Path> onChosen) {

    FileBrowserSession {
        extensions = Set.copyOf(extensions);
    }

    static FileBrowserSession folder(String title, Consumer<Path> onChosen) {
        return new FileBrowserSession(title, true, Set.of(), onChosen);
    }

    static FileBrowserSession file(String title, Set<String> extensions, Consumer<Path> onChosen) {
        return new FileBrowserSession(title, false, extensions, onChosen);
    }
}
