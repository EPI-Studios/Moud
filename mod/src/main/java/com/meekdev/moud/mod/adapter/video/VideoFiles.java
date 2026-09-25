package com.meekdev.moud.mod.adapter.video;

import com.meekdev.moud.mod.client.ClientPlace;
import com.meekdev.moud.mod.client.PlaceFiles;
import com.meekdev.moud.mod.place.SyncedFiles.Stamp;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class VideoFiles {

    private record Kept(Stamp stamp, Path file) {}

    private static final Map<String, Kept> KEPT = new HashMap<>();
    private static final List<Path> STALE = new ArrayList<>();

    private VideoFiles() {}

    static String key(String res) {
        return ClientPlace.root() + "|" + res;
    }

    static synchronized @Nullable Path file(String key, String res, Stamp stamp) throws IOException {
        Kept kept = KEPT.get(key);
        if (kept != null && kept.stamp().equals(stamp) && Files.isRegularFile(kept.file())) return kept.file();
        byte[] bytes = PlaceFiles.read(res);
        if (bytes == null) return null;
        Path file = Files.createTempFile("moud-video-", extension(res));
        file.toFile().deleteOnExit();
        Files.write(file, bytes);
        if (kept != null) STALE.add(kept.file());
        KEPT.put(key, new Kept(stamp, file));
        return file;
    }

    static synchronized void forget() {
        if (KEPT.isEmpty() && STALE.isEmpty()) return;
        for (Kept kept : KEPT.values()) STALE.add(kept.file());
        KEPT.clear();
        STALE.removeIf(VideoFiles::delete);
    }

    private static boolean delete(Path file) {
        try {
            Files.deleteIfExists(file);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? ".video" : name.substring(dot);
    }
}
