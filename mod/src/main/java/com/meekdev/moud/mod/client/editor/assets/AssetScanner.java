package com.meekdev.moud.mod.client.editor.assets;

import com.meekdev.moud.mod.place.ImportSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class AssetScanner {

    private static final Set<String> HIDDEN = Set.of("build", "target", "node_modules");
    private static final int SEARCH_DEPTH = 12;

    private AssetScanner() {}

    public static boolean hidden(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return name.startsWith(".") || HIDDEN.contains(name) || name.endsWith(ImportSettings.SUFFIX);
    }

    public static List<AssetEntry> list(Path directory) {
        List<AssetEntry> entries = new ArrayList<>();
        try (Stream<Path> children = Files.list(directory)) {
            children.filter(child -> !hidden(child)).forEach(child -> entries.add(entry(child)));
        } catch (IOException e) {
            return entries;
        }
        entries.sort(order());
        return entries;
    }

    public static List<AssetEntry> search(Path directory, String text) {
        String needle = text.toLowerCase(Locale.ROOT);
        List<AssetEntry> entries = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(directory, SEARCH_DEPTH)) {
            walk.filter(path -> !path.equals(directory))
                    .filter(path -> !insideHidden(directory, path))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).contains(needle))
                    .forEach(path -> entries.add(entry(path)));
        } catch (IOException | RuntimeException e) {
            return entries;
        }
        entries.sort(order());
        return entries;
    }

    public static List<Path> folders(Path directory) {
        List<Path> folders = new ArrayList<>();
        try (Stream<Path> children = Files.list(directory)) {
            children.filter(Files::isDirectory).filter(child -> !hidden(child)).forEach(folders::add);
        } catch (IOException e) {
            return folders;
        }
        folders.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));
        return folders;
    }

    static AssetEntry entry(Path path) {
        String name = path.getFileName().toString();
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            AssetKind kind = attributes.isDirectory() ? AssetKind.FOLDER : AssetKind.of(name);
            return new AssetEntry(name, path, kind, attributes.size(), attributes.lastModifiedTime().toMillis());
        } catch (IOException e) {
            return new AssetEntry(name, path, AssetKind.of(name), 0, 0);
        }
    }

    static boolean insideHiddenPublic(Path root, Path path) {
        return insideHidden(root, path);
    }

    private static boolean insideHidden(Path root, Path path) {
        for (Path at = path; at != null && !at.equals(root); at = at.getParent()) {
            if (hidden(at)) return true;
        }
        return false;
    }

    private static Comparator<AssetEntry> order() {
        return Comparator.comparing((AssetEntry entry) -> !entry.folder()).thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT));
    }
}
