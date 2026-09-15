package com.meekdev.moud.mod.place;

import com.meekdev.moud.mod.MoudMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.hollowcube.polar.PolarReader;
import net.hollowcube.polar.PolarWorld;
import org.jspecify.annotations.Nullable;

public final class Blocks {

    private static final String WORLD = "world.polar";

    private Blocks() {}

    public static @Nullable PolarWorld load() {
        Path path = PlaceToml.root().resolve(WORLD);
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
