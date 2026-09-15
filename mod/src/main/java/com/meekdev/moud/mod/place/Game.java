package com.meekdev.moud.mod.place;

import com.meekdev.moud.core.scene.Json;
import com.meekdev.moud.mod.MoudMod;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class Game {

    public static final String FOLDER = "moud-game";
    public static final String INFO = FOLDER + "/game.json";
    public static final String FILES = FOLDER + "/files.txt";
    public static final String PLACE = FOLDER + "/place/";
    private static final String STAMP = ".moud-game";

    public record Info(String id, String name, String version, String hash) {}

    private static @Nullable Info info;
    private static boolean read;

    private Game() {}

    public static boolean standalone() {
        return info() != null;
    }

    public static synchronized @Nullable Info info() {
        if (read) return info;
        read = true;
        try (InputStream in = Game.class.getClassLoader().getResourceAsStream(INFO)) {
            if (in == null) return null;
            if (Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)) instanceof Map<?, ?> map) {
                info = new Info(text(map, "id"), text(map, "name"), text(map, "version"), text(map, "hash"));
            }
        } catch (IOException | RuntimeException e) {
            MoudMod.LOG.error("the game inside this jar could not be read", e);
        }
        return info;
    }

    public static Path unpack(Path gameDir) {
        Info game = info();
        if (game == null) throw new IllegalStateException("this jar holds no game");
        Path root = gameDir.resolve("moud-games").resolve(game.id());
        Path stamp = root.resolve(STAMP);
        try {
            if (Files.isRegularFile(stamp) && Files.readString(stamp).strip().equals(game.hash())) return root;
            String listing;
            try (InputStream in = Game.class.getClassLoader().getResourceAsStream(FILES)) {
                if (in == null) throw new IOException(FILES + " is missing");
                listing = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            for (String line : listing.split("\n")) {
                String relative = line.strip();
                if (relative.isEmpty()) continue;
                Path target = root.resolve(relative).normalize();
                if (!target.startsWith(root)) throw new IOException(relative + " leaves the game folder");
                try (InputStream in = Game.class.getClassLoader().getResourceAsStream(PLACE + relative)) {
                    if (in == null) throw new IOException(relative + " is missing from the jar");
                    Files.createDirectories(target.getParent());
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.writeString(stamp, game.hash());
            MoudMod.LOG.info("unpacked {} {} into {}", game.name(), game.version(), root);
        } catch (IOException e) {
            throw new IllegalStateException("could not unpack the game: " + e.getMessage(), e);
        }
        return root;
    }

    private static String text(Map<?, ?> map, String key) {
        return map.get(key) instanceof String value ? value : "";
    }
}
