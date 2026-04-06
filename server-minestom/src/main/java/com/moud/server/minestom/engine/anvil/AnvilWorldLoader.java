package com.moud.server.minestom.engine.anvil;

import com.moud.core.scene.Node;
import com.moud.server.minestom.engine.Engine;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.anvil.AnvilLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class AnvilWorldLoader {

    public static final String TYPE_ID = "AnvilWorld";
    public static final String PROP_WORLD_PATH = "world_path";

    private final InstanceContainer instance;
    private final Engine engine;
    private long lastAppliedRevision = Long.MIN_VALUE;
    private String lastAppliedPath = "";

    public AnvilWorldLoader(InstanceContainer instance, Engine engine) {
        this.instance = Objects.requireNonNull(instance, "instance");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public void tick() {
        long rev = engine.sceneRevision();
        if (rev == lastAppliedRevision) return;
        lastAppliedRevision = rev;

        String worldPath = findAnvilWorldPath(engine.sceneTree().root());
        if (worldPath == null) worldPath = "";

        if (worldPath.equals(lastAppliedPath)) return;
        lastAppliedPath = worldPath;

        if (worldPath.isBlank()) return;

        Path resolved = Path.of(worldPath);
        if (!Files.isDirectory(resolved)) {
            System.err.println("[AnvilWorldLoader] World directory not found: " + worldPath);
            return;
        }

        System.out.println("[AnvilWorldLoader] Loading world from: " + resolved.toAbsolutePath());
        instance.setChunkLoader(new AnvilLoader(resolved));
    }

    private static String findAnvilWorldPath(Node node) {
        if (node == null) return null;

        String type = node.getProperty("@type");
        if (TYPE_ID.equals(type)) {
            String path = node.getProperty(PROP_WORLD_PATH);
            if (path != null && !path.isBlank()) return path.trim();
        }

        for (Node child : node.children()) {
            String found = findAnvilWorldPath(child);
            if (found != null) return found;
        }
        return null;
    }
}
