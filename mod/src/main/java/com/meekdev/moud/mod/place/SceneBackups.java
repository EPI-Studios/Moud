package com.meekdev.moud.mod.place;

import com.meekdev.moud.core.asset.Res;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class SceneBackups {

    public enum Kind { AUTO, BEFORE_SAVE }

    public record Backup(Path file, long time, Kind kind) {}

    public static final int KEPT = 20;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private SceneBackups() {}

    public static Path folder(Path root) {
        return root.resolve(".moud").resolve("backups");
    }

    public static Path folder(Path root, String scene) {
        return folder(root).resolve(Res.parse(scene).replace('/', '_'));
    }

    public static Path write(Path root, String scene, String text, Kind kind) throws IOException {
        Path folder = folder(root, scene);
        Files.createDirectories(folder);
        String suffix = kind == Kind.AUTO ? "-auto.scene" : "-saved.scene";
        String stamp = LocalDateTime.now().format(STAMP);
        Path file = folder.resolve(stamp + suffix);
        for (int n = 2; Files.exists(file); n++) file = folder.resolve(stamp + "-" + n + suffix);
        Files.writeString(file, text);
        List<Backup> all = list(root, scene);
        for (int n = KEPT; n < all.size(); n++) Files.deleteIfExists(all.get(n).file());
        return file;
    }

    public static List<Backup> list(Path root, String scene) {
        Path folder = folder(root, scene);
        List<Backup> found = new ArrayList<>();
        if (!Files.isDirectory(folder)) return found;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".scene")).toList()) {
                Kind kind = file.getFileName().toString().endsWith("-auto.scene") ? Kind.AUTO : Kind.BEFORE_SAVE;
                found.add(new Backup(file, modified(file), kind));
            }
        } catch (IOException ignored) {
        }
        found.sort(Comparator.comparingLong(Backup::time).thenComparing(backup -> backup.file().getFileName().toString()).reversed());
        return found;
    }

    public static @Nullable Backup unsavedWork(Path root, String scene) {
        Path sceneFile = root.resolve(Res.parse(scene));
        long saved = Files.isRegularFile(sceneFile) ? modified(sceneFile) : 0;
        for (Backup backup : list(root, scene)) {
            if (backup.kind() != Kind.AUTO) continue;
            if (backup.time() <= saved) return null;
            try {
                String text = Files.readString(backup.file());
                String disk = Files.isRegularFile(sceneFile) ? Files.readString(sceneFile) : "";
                return text.equals(disk) ? null : backup;
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    public static boolean inside(Path root, Path file) {
        return file.toAbsolutePath().normalize().startsWith(folder(root).toAbsolutePath().normalize());
    }

    private static long modified(Path file) {
        try {
            FileTime time = Files.getLastModifiedTime(file);
            return time.toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
