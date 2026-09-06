package com.meekdev.moud.mod.place;

import com.meekdev.moud.mod.MoudMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.hollowcube.polar.PolarReader;
import net.hollowcube.polar.PolarWorld;
import org.jspecify.annotations.Nullable;

// a place is a directory beside the game in dev, and the jar itself once exported
public final class Place {

    private static final String WORLD = "place/world.polar";

    private Place() {}

    public static @Nullable PolarWorld world() {
        Path path = FabricLoader.getInstance().getGameDir().resolve(WORLD);
        if (!Files.isRegularFile(path)) {
            MoudMod.LOG.info("no {}, the level stays empty", path);
            return null;
        }
        try {
            PolarWorld world = PolarReader.read(Files.readAllBytes(path));
            MoudMod.LOG.info("read {} chunks from {}", world.chunks().size(), path);
            return world;
        } catch (IOException | RuntimeException e) {
            MoudMod.LOG.error("could not read {}", path, e);
            return null;
        }
    }
}
