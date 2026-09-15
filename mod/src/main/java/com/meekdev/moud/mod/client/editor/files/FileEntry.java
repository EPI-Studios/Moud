package com.meekdev.moud.mod.client.editor.files;

import java.nio.file.Path;
import java.util.Locale;

public record FileEntry(Path path, String name, boolean directory, boolean hidden) {

    public boolean hasExtension(String extension) {
        return name.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT));
    }

    public boolean matches(String search) {
        return search.isBlank()
                || name.toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT));
    }
}
