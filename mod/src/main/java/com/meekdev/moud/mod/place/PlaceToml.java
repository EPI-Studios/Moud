package com.meekdev.moud.mod.place;

import com.meekdev.moud.core.place.PlaceConfig;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Features;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

// the running place's place.toml, read once at startup on each side
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
            // a broken file must not take the game down with it, and must not be quiet either
            MoudMod.LOG.error("place.toml cannot be read, running on the defaults: {}", wrong.getMessage());
            return PlaceConfig.DEFAULT;
        }
    }

    // the engine's own starting set, then whatever the place turned on or off
    public static void apply(Features features) {
        Switches.install(features);
        for (Map.Entry<String, Boolean> one : config().features().entrySet()) {
            if (features.lookup(one.getKey()) == null) {
                MoudMod.LOG.error("place.toml turns on '{}', which is not a feature. the features are {}",
                        one.getKey(), features.all().stream().map(Features.Switch::key).toList());
                continue;
            }
            features.set(one.getKey(), one.getValue());
        }
    }
}
