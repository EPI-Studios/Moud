package com.meekdev.moud.mod.client.editor.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public record SwapTerrain(Path terrain, Path stash, String label) implements Edit {

    @Override
    public void apply(SceneDocument document) {
        Path aside = terrain.resolveSibling(terrain.getFileName() + ".swap");
        try {
            if (Files.exists(terrain)) Files.move(terrain, aside, StandardCopyOption.REPLACE_EXISTING);
            if (Files.exists(stash)) {
                Files.createDirectories(terrain.getParent());
                Files.move(stash, terrain, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.exists(aside)) {
                Files.createDirectories(stash.getParent());
                Files.move(aside, stash, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not swap the terrain: " + e.getMessage());
        }
        SceneLink.reloadTerrain();
    }

    @Override
    public Edit invert(SceneDocument document) {
        return this;
    }
}
