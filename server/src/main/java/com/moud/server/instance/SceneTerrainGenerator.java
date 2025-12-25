package com.moud.server.instance;

import com.moud.server.editor.SceneDefaults;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;
import net.minestom.server.instance.generator.UnitModifier;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class SceneTerrainGenerator {
    private static final int MAX_SIZE = 4096;
    private static final int MIN_SURFACE_Y = 1;
    private static final int MAX_SURFACE_Y = 255;

    private final AtomicReference<TerrainSettings> settings = new AtomicReference<>(defaultSettings());

    public void setSettings(TerrainSettings settings) {
        if (settings == null) {
            return;
        }
        this.settings.set(settings);
    }

    public TerrainSettings getSettings() {
        return settings.get();
    }

    public void generate(GenerationUnit unit) {
        if (unit == null) {
            return;
        }
        TerrainSettings current = settings.get();
        if (current == null) {
            return;
        }

        int minCoord = current.minCoord();
        int maxCoord = current.maxCoord();

        Point start = unit.absoluteStart();
        Point size = unit.size();
        int startX = start.blockX();
        int startZ = start.blockZ();
        int endX = startX + Math.max(1, size.blockX()) - 1;
        int endZ = startZ + Math.max(1, size.blockZ()) - 1;

        int clampedStartX = Math.max(minCoord, startX);
        int clampedEndX = Math.min(maxCoord, endX);
        if (clampedStartX > clampedEndX) {
            return;
        }

        int clampedStartZ = Math.max(minCoord, startZ);
        int clampedEndZ = Math.min(maxCoord, endZ);
        if (clampedStartZ > clampedEndZ) {
            return;
        }

        UnitModifier modifier = unit.modifier();
        boolean placeBelow = current.surfaceY() - 1 >= MIN_SURFACE_Y;

        for (int worldX = clampedStartX; worldX <= clampedEndX; worldX++) {
            for (int worldZ = clampedStartZ; worldZ <= clampedEndZ; worldZ++) {
                modifier.setBlock(worldX, current.surfaceY(), worldZ, current.surfaceBlock());
                if (placeBelow) {
                    modifier.setBlock(worldX, current.surfaceY() - 1, worldZ, current.fillBlock());
                }
            }
        }
    }

    public static TerrainSettings defaultSettings() {
        Block surfaceBlock = resolveBlock(SceneDefaults.DEFAULT_SURFACE_BLOCK);
        Block fillBlock = resolveBlock(SceneDefaults.DEFAULT_FILL_BLOCK);
        return new TerrainSettings(SceneDefaults.BASE_SCENE_SIZE_BLOCKS, clampHeight(SceneDefaults.BASE_TERRAIN_HEIGHT), surfaceBlock, fillBlock);
    }

    public static TerrainSettings clampSettings(int size, int surfaceY, Block surfaceBlock, Block fillBlock) {
        int clampedSize = clampSize(size);
        int clampedSurfaceY = clampHeight(surfaceY);
        Block resolvedSurface = Objects.requireNonNullElse(surfaceBlock, Block.AIR);
        Block resolvedFill = Objects.requireNonNullElse(fillBlock, Block.AIR);
        return new TerrainSettings(clampedSize, clampedSurfaceY, resolvedSurface, resolvedFill);
    }

    private static Block resolveBlock(String namespaceId) {
        Block resolved = Block.fromNamespaceId(namespaceId);
        return resolved != null ? resolved : Block.AIR;
    }

    private static int clampSize(int size) {
        return Math.max(1, Math.min(MAX_SIZE, size));
    }

    private static int clampHeight(int height) {
        return Math.max(MIN_SURFACE_Y, Math.min(MAX_SURFACE_Y, height));
    }

    public record TerrainSettings(int size, int surfaceY, Block surfaceBlock, Block fillBlock) {
        public int minCoord() {
            int half = size / 2;
            return -half;
        }

        public int maxCoord() {
            return minCoord() + size - 1;
        }
    }
}
