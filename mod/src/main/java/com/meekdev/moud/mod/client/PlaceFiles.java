package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.asset.Res;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class PlaceFiles {

    private PlaceFiles() {}

    public static byte @Nullable [] read(String res) {
        Path root = ClientPlace.root();
        if (root == null) return null;
        try {
            Path file = root.resolve(Res.parse(res));
            return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        } catch (IOException | IllegalArgumentException ignored) {
            return null;
        }
    }

    public static Identifier idOf(String res) {
        String path = Res.parse(res).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        return Identifier.fromNamespaceAndPath("moud", "place/" + path);
    }
}
