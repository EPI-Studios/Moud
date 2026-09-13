package com.meekdev.moud.mod.place;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.script.api.FileRef;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

// the place's files for a script: read anything under the place, write scenes
public final class PlaceFileRef implements FileRef {

    private final Path root;

    public PlaceFileRef(Path root) {
        this.root = root;
    }

    @Override
    public String read(String res) {
        Path file = root.resolve(Res.parse(res));
        if (!Files.isRegularFile(file)) return null;
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void write(String res, String text) {
        Path file = root.resolve(Res.parse(res));
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new IllegalStateException("could not write " + res + ": " + e.getMessage());
        }
    }
}
