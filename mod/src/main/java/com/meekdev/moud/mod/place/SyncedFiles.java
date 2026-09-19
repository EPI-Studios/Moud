package com.meekdev.moud.mod.place;

import com.meekdev.moud.core.asset.Res;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

public final class SyncedFiles {

    public static final List<String> EXTENSIONS = List.of("anim", "bbmodel");
    public static final int MAX_BYTES = 8 * 1024 * 1024;

    private record Copy(byte @Nullable [] bytes) {}

    private static final Map<String, Copy> FROM_SERVER = new ConcurrentHashMap<>();

    private final Map<String, byte @Nullable []> changed = new LinkedHashMap<>();

    public static boolean synced(String res) {
        String lower = res.toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(extension -> lower.endsWith("." + extension));
    }

    public static boolean clip(String res) {
        return res.toLowerCase(Locale.ROOT).endsWith(".anim");
    }

    public static @Nullable String res(Path root, Path file) {
        Path top = root.toAbsolutePath().normalize();
        Path at = file.toAbsolutePath().normalize();
        if (!at.startsWith(top) || at.equals(top)) return null;
        Path relative = top.relativize(at);
        for (Path segment : relative) {
            if (segment.toString().equals(".moud")) return null;
        }
        String res = Res.SCHEME + relative.toString().replace('\\', '/');
        return synced(res) ? res : null;
    }

    public static Path file(Path root, String res) {
        String path = Res.parse(res);
        if (!synced(res)) throw new IllegalArgumentException(res + " is not a .anim or .bbmodel file");
        Path top = root.toAbsolutePath().normalize();
        Path file = top.resolve(path).normalize();
        if (!file.startsWith(top) || res(top, file) == null) throw new IllegalArgumentException(res + " is not inside the place");
        return file;
    }

    public static void write(Path root, String res, byte[] bytes) throws IOException {
        Path file = file(root, res);
        Path parent = file.getParent();
        Files.createDirectories(parent);
        if (!parent.toRealPath().startsWith(root.toRealPath())) throw new IOException(res + " leads out of the place");
        if (Files.isSymbolicLink(file) || Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(res + " is not a plain file");
        }
        Path temporary = Files.createTempFile(parent, ".sync-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public boolean note(String res, byte @Nullable [] bytes) {
        if (changed.containsKey(res) && Arrays.equals(changed.get(res), bytes)) return false;
        changed.put(res, bytes);
        return true;
    }

    public Map<String, byte @Nullable []> changed() {
        return changed;
    }

    public void clear() {
        changed.clear();
    }

    public static void arrived(String res, byte @Nullable [] bytes) {
        FROM_SERVER.put(res, new Copy(bytes));
    }

    public static Set<String> dropCopies() {
        Set<String> dropped = Set.copyOf(FROM_SERVER.keySet());
        FROM_SERVER.clear();
        return dropped;
    }

    public static byte @Nullable [] read(Path root, String res) throws IOException {
        Copy copy = FROM_SERVER.get(res);
        if (copy != null) return copy.bytes();
        Path file = root.resolve(Res.parse(res));
        return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
    }
}
