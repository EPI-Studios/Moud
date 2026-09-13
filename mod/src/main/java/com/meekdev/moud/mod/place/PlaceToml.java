package com.meekdev.moud.mod.place;

import com.meekdev.moud.core.place.PlaceConfig;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Features;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public final class PlaceToml {

    private static PlaceConfig config;

    private PlaceToml() {}

    public static Path root() {
        return FabricLoader.getInstance().getGameDir().resolve("place");
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
        } catch (IOException | IllegalArgumentException wrong) {
            MoudMod.LOG.error("failed to read place.toml, using defaults: {}", wrong.getMessage());
            return PlaceConfig.DEFAULT;
        }
    }

    public static void apply(Features features) {
        Switches.install(features);
        for (Map.Entry<String, Boolean> one : config().features().entrySet()) {
            if (features.lookup(one.getKey()) == null) {
                MoudMod.LOG.error("unknown feature '{}' in place.toml, expected one of {}",
                        one.getKey(), features.all().stream().map(Features.Switch::key).toList());
                continue;
            }
            features.set(one.getKey(), one.getValue());
        }
    }
}
