package com.meekdev.moud.mod.client.editor.files;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record FilePlace(String label, Path path) {

    public static List<FilePlace> discover(Path home) {
        Set<Path> seen = new LinkedHashSet<>();
        List<FilePlace> places = new ArrayList<>();
        addPlace(places, seen, home.getFileName() == null ? home.toString()
                : home.getFileName().toString(), home);
        for (String name : List.of("Desktop", "Documents", "Downloads")) {
            addPlace(places, seen, name, home.resolve(name));
        }
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            addPlace(places, seen, root.toString(), root);
        }
        return List.copyOf(places);
    }

    private static void addPlace(List<FilePlace> places, Set<Path> seen, String label, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized) || !seen.add(normalized)) {
            return;
        }
        places.add(new FilePlace(label, normalized));
    }
}
