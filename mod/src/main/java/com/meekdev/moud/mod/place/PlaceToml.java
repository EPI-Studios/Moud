package com.meekdev.moud.mod.place;

import com.meekdev.moud.core.place.PlaceConfig;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Features;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

public final class PlaceToml {

    private static PlaceConfig config;
    private static @Nullable Path opened;
    private static boolean editOnStart;
    private static final Map<String, Boolean> REPLACED = new LinkedHashMap<>();

    private PlaceToml() {}

    public static Path root() {
        if (opened != null) return opened;
        String chosen = System.getProperty("moud.place");
        Path game = FabricLoader.getInstance().getGameDir();
        return chosen == null || chosen.isBlank() ? game.resolve("place") : game.resolve(chosen);
    }

    public static PlaceConfig config() {
        if (config == null) config = read();
        return config;
    }

    private static PlaceConfig read() {
        Path file = root().resolve("place.toml");
        if (!Files.isRegularFile(file)) return PlaceConfig.DEFAULT;
        try {
            return PlaceConfig.parse(Files.readString(file));
        } catch (IOException | IllegalArgumentException e) {
            MoudMod.LOG.error("failed to read place.toml, using defaults: {}", e.getMessage());
            return PlaceConfig.DEFAULT;
        }
    }

    public static boolean chosenAtLaunch() {
        String chosen = System.getProperty("moud.place");
        return chosen != null && !chosen.isBlank();
    }

    public static boolean editOnStart() {
        return editOnStart;
    }

    public static void open(Path root, boolean edit, Features features) {
        for (Map.Entry<String, Boolean> one : REPLACED.entrySet()) features.set(one.getKey(), one.getValue());
        REPLACED.clear();
        opened = root.toAbsolutePath().normalize();
        editOnStart = edit;
        config = null;
        apply(features);
    }

    public static void apply(Features features) {
        Switches.install(features);
        for (Map.Entry<String, Boolean> one : config().features().entrySet()) {
            Features.Switch known = features.lookup(one.getKey());
            if (known != null) REPLACED.putIfAbsent(one.getKey(), features.isOn(known));
            if (known == null) {
                MoudMod.LOG.error("unknown feature '{}' in place.toml, expected one of {}",
                        one.getKey(), features.all().stream().map(Features.Switch::key).toList());
                continue;
            }
            features.set(one.getKey(), one.getValue());
        }
    }
}
