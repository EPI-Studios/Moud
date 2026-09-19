package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.mod.client.editor.assets.AssetFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

final class ClipLibrary {

    static final String FOLDER = "animations";
    static final String EXTENSION = ".anim";
    private static final long RESCAN_MILLIS = 1500;

    record Listed(Path path, String name, double length, AnimClip.Space space, @Nullable String error) {}

    static final class Open {
        AnimClip clip;
        String saved;
        final boolean v1;
        private @Nullable AnimClip measured;
        private boolean dirty;

        Open(AnimClip clip, String saved, boolean v1) {
            this.clip = clip;
            this.saved = saved;
            this.v1 = v1;
        }

        boolean dirty() {
            if (measured != clip) {
                measured = clip;
                dirty = !ClipFile.write(clip).equals(saved);
            }
            return dirty;
        }

        void savedAs(String text) {
            saved = text;
            measured = null;
        }
    }

    private record Seen(long modified, Listed listed) {}

    private final Map<Path, Open> open = new HashMap<>();
    private final Map<Path, Seen> seen = new HashMap<>();
    private List<Listed> listed = List.of();
    private long scannedAt;

    Path folder() {
        return AssetFiles.root().resolve(FOLDER);
    }

    List<Listed> clips() {
        long now = System.currentTimeMillis();
        if (now - scannedAt > RESCAN_MILLIS) {
            scannedAt = now;
            listed = scan();
        }
        return listed;
    }

    void rescan() {
        scannedAt = 0;
    }

    private List<Listed> scan() {
        Path folder = folder();
        if (!Files.isDirectory(folder)) return List.of();
        List<Listed> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path path : walk.filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXTENSION)).toList()) {
                found.add(describe(path));
            }
        } catch (IOException e) {
            return listed;
        }
        found.sort(Comparator.comparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
        return found;
    }

    private Listed describe(Path path) {
        String name = name(path);
        Open loaded = open.get(path);
        if (loaded != null) return new Listed(path, name, loaded.clip.length, loaded.clip.space, null);
        try {
            long modified = Files.getLastModifiedTime(path).toMillis();
            Seen known = seen.get(path);
            if (known != null && known.modified() == modified) return known.listed();
            AnimClip clip = ClipFile.read(Files.readString(path, StandardCharsets.UTF_8));
            Listed entry = new Listed(path, name, clip.length, clip.space, null);
            seen.put(path, new Seen(modified, entry));
            return entry;
        } catch (IOException | RuntimeException e) {
            return new Listed(path, name, 0, AnimClip.Space.BODY, e.getMessage() == null ? "could not be read" : e.getMessage());
        }
    }

    static String name(Path path) {
        String file = path.getFileName().toString();
        return file.toLowerCase(Locale.ROOT).endsWith(EXTENSION) ? file.substring(0, file.length() - EXTENSION.length()) : file;
    }

    @Nullable Open get(Path path) {
        return open.get(path);
    }

    Open load(Path path) throws IOException {
        Open known = open.get(path);
        if (known != null) return known;
        String text = Files.readString(path, StandardCharsets.UTF_8);
        boolean v1 = ClipFile.isV1(text);
        AnimClip clip = ClipFile.read(text);
        Open loaded = new Open(clip, ClipFile.write(clip), v1);
        open.put(path, loaded);
        return loaded;
    }

    Open create(Path path, AnimClip clip) {
        Open made = new Open(clip, "", false);
        open.put(path, made);
        return made;
    }

    void save(Path path) throws IOException {
        Open loaded = open.get(path);
        if (loaded == null) return;
        String text = ClipFile.write(loaded.clip);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
        loaded.savedAs(text);
        seen.remove(path);
        rescan();
    }

    boolean anyDirty() {
        for (Open loaded : open.values()) {
            if (loaded.dirty()) return true;
        }
        return false;
    }

    void forgetAll() {
        open.clear();
        seen.clear();
        rescan();
    }
}
