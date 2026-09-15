package com.meekdev.moud.mod.client.editor.assets;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.mod.place.PlaceToml;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class AssetFiles {

    private AssetFiles() {}

    public static Path root() {
        return PlaceToml.root().toAbsolutePath().normalize();
    }

    public static @Nullable String res(Path path) {
        Path relative;
        try {
            relative = root().relativize(path.toAbsolutePath().normalize());
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (relative.startsWith("..") || relative.toString().isEmpty()) return null;
        return Res.SCHEME + relative.toString().replace('\\', '/');
    }

    public static Path unique(Path directory, String fileName) {
        Path target = directory.resolve(fileName);
        if (!Files.exists(target)) return target;
        int dot = fileName.indexOf('.', 1);
        String stem = dot < 0 ? fileName : fileName.substring(0, dot);
        String extension = dot < 0 ? "" : fileName.substring(dot);
        for (int n = 2; ; n++) {
            target = directory.resolve(stem + " " + n + extension);
            if (!Files.exists(target)) return target;
        }
    }

    public static List<Path> importInto(Path directory, List<Path> sources) throws IOException {
        List<Path> copied = new ArrayList<>();
        for (Path source : sources) {
            if (!Files.isRegularFile(source)) continue;
            if (source.toAbsolutePath().normalize().getParent().equals(directory.toAbsolutePath().normalize())) {
                copied.add(source);
                continue;
            }
            Path target = unique(directory, source.getFileName().toString());
            Files.copy(source, target);
            copied.add(target);
        }
        return copied;
    }

    public static Path rename(Path path, String name) throws IOException {
        String clean = name.strip();
        if (clean.isEmpty() || clean.contains("/") || clean.contains("\\") || clean.equals(".") || clean.equals("..")) {
            throw new IOException("\"" + name + "\" is not a file name");
        }
        String original = path.getFileName().toString();
        int dot = original.indexOf('.', 1);
        if (!Files.isDirectory(path) && dot > 0 && clean.indexOf('.', 1) < 0) clean += original.substring(dot);
        Path target = path.resolveSibling(clean);
        if (target.equals(path)) return path;
        if (Files.exists(target)) throw new IOException(clean + " already exists");
        return Files.move(path, target);
    }

    public static Path duplicate(Path path) throws IOException {
        Path target = unique(path.getParent(), path.getFileName().toString());
        if (!Files.isDirectory(path)) return Files.copy(path, target);
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                Files.createDirectories(target.resolve(path.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.copy(file, target.resolve(path.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
        return target;
    }

    public static Path move(Path path, Path directory) throws IOException {
        Path target = directory.resolve(path.getFileName().toString());
        if (target.equals(path)) return path;
        if (directory.toAbsolutePath().normalize().startsWith(path.toAbsolutePath().normalize())) throw new IOException("a folder can not go inside itself");
        if (Files.exists(target)) throw new IOException(path.getFileName() + " is already there");
        return Files.move(path, target);
    }

    public static void delete(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            Files.deleteIfExists(path);
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
