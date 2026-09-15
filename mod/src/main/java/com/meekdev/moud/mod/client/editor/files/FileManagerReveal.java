package com.meekdev.moud.mod.client.editor.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class FileManagerReveal {

    private FileManagerReveal() {
    }

    public static Optional<String> reveal(Path target) {
        Path directory = Files.isDirectory(target) ? target : target.getParent();
        if (directory == null) {
            return Optional.of("no containing directory");
        }
        try {
            new ProcessBuilder(command(directory)).start();
            return Optional.empty();
        } catch (IOException unavailable) {
            return Optional.of(unavailable.getMessage() == null
                    ? "file manager unavailable" : unavailable.getMessage());
        }
    }

    private static List<String> command(Path directory) {
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String path = directory.toAbsolutePath().toString();
        if (operatingSystem.contains("win")) {
            return List.of("explorer", path);
        }
        if (operatingSystem.contains("mac")) {
            return List.of("open", path);
        }
        return List.of("xdg-open", path);
    }
}
