package com.moud.server.editor;

/**
 * Central place for base scene constants so the editor, world bootstrapper, and
 * SDK stay in sync with the same assumptions.
 */
public final class SceneDefaults {
    private SceneDefaults() {}

    public static final String DEFAULT_SCENE_ID = "default";
    /** Length of one side (in blocks) for the starter terrain. */
    public static final int BASE_SCENE_SIZE_BLOCKS = 1000;
    /** Height (in blocks) of the generated grass platform. */
    public static final int BASE_TERRAIN_HEIGHT = 64;
    public static final String DEFAULT_SURFACE_BLOCK = "minecraft:grass_block";
    public static final String DEFAULT_FILL_BLOCK = "minecraft:dirt";

    /** Convenience helper used when centering players above the platform. */
    public static double defaultSpawnY() {
        return BASE_TERRAIN_HEIGHT + 1.0;
    }
}
