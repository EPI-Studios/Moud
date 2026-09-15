package com.meekdev.moud.mod.place;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.mod.MoudMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.hollowcube.polar.PolarReader;
import net.hollowcube.polar.PolarWorld;
import org.jspecify.annotations.Nullable;

public final class Blocks {

    public static final String LEGACY = "world.polar";
    public static final String EXTENSION = ".polar";

    private Blocks() {}

    public static @Nullable PolarWorld load() {
        return load(startScene());
    }

    public static @Nullable PolarWorld load(String scene) {
        Path path = terrainOf(PlaceToml.root(), scene);
        if (path == null) {
            MoudMod.LOG.info("{} has no terrain, the level stays empty", scene);
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

    public static Path terrainFile(Path root, String scene) {
        String path = Res.parse(scene);
        String stem = path.endsWith(".scene") ? path.substring(0, path.length() - ".scene".length()) : path;
        return root.resolve(stem + EXTENSION);
    }

    public static @Nullable Path terrainOf(Path root, String scene) {
        Path own = terrainFile(root, scene);
        if (Files.isRegularFile(own)) return own;
        Path legacy = root.resolve(LEGACY);
        return scene.equals(startScene()) && Files.isRegularFile(legacy) ? legacy : null;
    }

    public static String startScene() {
        String scene = PlaceToml.config().scene();
        return scene.isEmpty() ? Place.DEFAULT_SCENE : scene;
    }
}
