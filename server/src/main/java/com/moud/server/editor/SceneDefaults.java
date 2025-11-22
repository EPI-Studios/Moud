package com.moud.server.editor;

public final class SceneDefaults {
    private SceneDefaults() {}

    public static final String DEFAULT_SCENE_ID = "default";

    public static final int BASE_SCENE_SIZE_BLOCKS = 1000;

    public static final int BASE_TERRAIN_HEIGHT = 64;
    public static final String DEFAULT_SURFACE_BLOCK = "minecraft:grass_block";
    public static final String DEFAULT_FILL_BLOCK = "minecraft:dirt";

    public static double defaultSpawnY() {
        return BASE_TERRAIN_HEIGHT + 1.0;
    }
}