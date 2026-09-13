package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.asset.Res;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

// the running place's own files, for the client adapters that load them
public final class PlaceFiles {

    private PlaceFiles() {}

    public static byte @Nullable [] read(String res) {
        Path root = ClientPlace.root();
        if (root == null) return null;
        try {
            Path file = root.resolve(Res.parse(res));
            return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        } catch (IOException | IllegalArgumentException unreadable) {
            return null;
        }
    }

    // the game only takes lowercase letters, digits and ._-/ in an id, so anything else is folded
    public static Identifier idOf(String res) {
        String path = Res.parse(res).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        return Identifier.fromNamespaceAndPath("moud", "place/" + path);
    }
}
