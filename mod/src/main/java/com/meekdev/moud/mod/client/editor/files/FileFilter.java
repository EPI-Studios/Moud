package com.meekdev.moud.mod.client.editor.files;

import java.util.List;
import java.util.Set;

public record FileFilter(Set<String> extensions, boolean foldersOnly, boolean showHidden,
                         String search) {

    public FileFilter {
        extensions = Set.copyOf(extensions);
    }

    public static FileFilter folders(boolean showHidden, String search) {
        return new FileFilter(Set.of(), true, showHidden, search);
    }

    public static FileFilter files(Set<String> extensions, boolean showHidden, String search) {
        return new FileFilter(extensions, false, showHidden, search);
    }

    public boolean accepts(FileEntry entry) {
        if (entry.hidden() && !showHidden) {
            return false;
        }
        if (entry.directory()) {
            return entry.matches(search);
        }
        return !foldersOnly && entry.matches(search) && hasAllowedExtension(entry);
    }

    public boolean allows(FileEntry entry) {
        return entry.directory() || (!foldersOnly && hasAllowedExtension(entry));
    }

    private boolean hasAllowedExtension(FileEntry entry) {
        return extensions.isEmpty() || extensions.stream().anyMatch(entry::hasExtension);
    }

    public List<String> describedExtensions() {
        return extensions.stream().sorted().toList();
    }
}
