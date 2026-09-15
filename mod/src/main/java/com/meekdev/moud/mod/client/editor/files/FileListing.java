package com.meekdev.moud.mod.client.editor.files;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record FileListing(Path directory, List<FileEntry> entries, Optional<Failure> failure) {

    public enum Failure {
        MISSING,
        UNREADABLE
    }

    private static final Comparator<FileEntry> ORDER =
            Comparator.comparing(FileEntry::directory).reversed()
                    .thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT));

    public FileListing {
        entries = List.copyOf(entries);
    }

    public static FileListing read(Path directory) {
        Path normalized = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            return failed(normalized, Failure.MISSING);
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(normalized)) {
            return new FileListing(normalized, collect(stream), Optional.empty());
        } catch (IOException | RuntimeException unreadable) {
            return failed(normalized, Failure.UNREADABLE);
        }
    }

    private static List<FileEntry> collect(DirectoryStream<Path> stream) {
        List<FileEntry> collected = new ArrayList<>();
        for (Path path : stream) {
            collected.add(entryOf(path));
        }
        collected.sort(ORDER);
        return collected;
    }

    private static FileEntry entryOf(Path path) {
        String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
        return new FileEntry(path, name, Files.isDirectory(path), name.startsWith("."));
    }

    private static FileListing failed(Path directory, Failure reason) {
        return new FileListing(directory, List.of(), Optional.of(reason));
    }

    public List<FileEntry> visible(FileFilter filter) {
        return entries.stream().filter(filter::accepts).toList();
    }

    public Optional<Path> parent() {
        return Optional.ofNullable(directory.getParent());
    }
}
