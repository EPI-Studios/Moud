package com.meekdev.moud.script.engine;

import com.meekdev.moud.script.api.ModuleSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PlaceModules implements ModuleSource {

    public static final String SHARED = "shared";

    private final Path root;
    private final String side;
    private final String other;

    public PlaceModules(Path root, boolean client) {
        this.root = root;
        this.side = client ? "client" : "server";
        this.other = client ? "server" : "client";
    }

    @Override
    public String read(String path) {
        String top = path.substring(0, Math.max(0, path.indexOf('/')));
        if (top.equals(other)) {
            throw new IllegalArgumentException("res://" + path + " belongs to the " + other + ", and "
                    + side + " scripts can only require from " + side + "/ and " + SHARED + "/");
        }
        if (!top.equals(side) && !top.equals(SHARED)) {
            throw new IllegalArgumentException("res://" + path + " is not a module. modules live in "
                    + side + "/ or " + SHARED + "/");
        }
        Path file = root.resolve(path);
        if (!Files.isRegularFile(file)) return null;
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
