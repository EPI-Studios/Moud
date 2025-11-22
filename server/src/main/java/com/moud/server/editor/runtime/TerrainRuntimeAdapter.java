package com.moud.server.editor.runtime;

import com.moud.network.MoudPackets;
import com.moud.server.editor.SceneDefaults;
import com.moud.server.instance.InstanceManager;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.batch.BatchOption;
import net.minestom.server.instance.batch.ChunkBatch;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.chunk.ChunkCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

public final class TerrainRuntimeAdapter implements SceneRuntimeAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerrainRuntimeAdapter.class);
    private static final ChunkCallback NOOP_CALLBACK = chunk -> {};
    private static final int MAX_SIZE = 4096;
    private static final int MIN_SURFACE_Y = 1;
    private static final int MAX_SURFACE_Y = 255;

    private final String sceneId;
    private final InstanceContainer instance;
    private final BatchOption batchOption;

    private int lastSize;
    private int lastSurfaceY;
    private Block lastSurfaceBlock;
    private Block lastFillBlock;

    public TerrainRuntimeAdapter(String sceneId) {
        this.sceneId = sceneId;
        this.instance = InstanceManager.getInstance().getDefaultInstance();
        this.batchOption = new BatchOption()
                .setCalculateInverse(false)
                .setSendUpdate(true)
                .setFullChunk(false)
                .setUnsafeApply(false);
    }

    @Override
    public synchronized void create(MoudPackets.SceneObjectSnapshot snapshot) {
        applyTerrain(snapshot);
    }

    @Override
    public synchronized void update(MoudPackets.SceneObjectSnapshot snapshot) {
        applyTerrain(snapshot);
    }

    @Override
    public synchronized void remove() {
        if (lastSize > 0) {
            clearArea(lastSize, lastSurfaceY);
            lastSize = 0;
        }
    }

    private void applyTerrain(MoudPackets.SceneObjectSnapshot snapshot) {
        Map<String, Object> props = snapshot.properties();
        int requestedSize = intProperty(props.get("size"), SceneDefaults.BASE_SCENE_SIZE_BLOCKS);
        int size = clampSize(requestedSize);
        int requestedHeight = intProperty(props.get("terrainHeight"), SceneDefaults.BASE_TERRAIN_HEIGHT);
        int surfaceY = clampHeight(requestedHeight);
        Block surfaceBlock = blockProperty(props.get("surfaceBlock"), SceneDefaults.DEFAULT_SURFACE_BLOCK);
        Block fillBlock = blockProperty(props.get("fillBlock"), SceneDefaults.DEFAULT_FILL_BLOCK);

        boolean unchanged = size == lastSize
                && surfaceY == lastSurfaceY
                && surfaceBlock == lastSurfaceBlock
                && fillBlock == lastFillBlock;
        if (unchanged) {
            return;
        }

        if (lastSize > 0 && (size != lastSize || surfaceY != lastSurfaceY)) {
            clearArea(lastSize, lastSurfaceY);
        }

        long start = System.currentTimeMillis();
        paintArea(size, surfaceY, surfaceBlock, fillBlock);
        long duration = System.currentTimeMillis() - start;

        lastSize = size;
        lastSurfaceY = surfaceY;
        lastSurfaceBlock = surfaceBlock;
        lastFillBlock = fillBlock;

        LOGGER.info("Scene '{}' terrain prepared ({}x{} @ y={}) in {} ms",
                sceneId, size, size, surfaceY, duration);
    }

    private void clearArea(int size, int surfaceY) {
        long start = System.currentTimeMillis();
        paintArea(size, surfaceY, Block.AIR, Block.AIR);
        long duration = System.currentTimeMillis() - start;
        LOGGER.info("Scene '{}' terrain cleared ({}x{} @ y={}) in {} ms",
                sceneId, size, size, surfaceY, duration);
    }

    private void paintArea(int size, int surfaceY, Block surfaceBlock, Block fillBlock) {
        int minX = computeMinCoord(size);
        int maxX = computeMaxCoord(size, minX);
        int minZ = minX;
        int maxZ = maxX;

        int minChunkX = Math.floorDiv(minX, Chunk.CHUNK_SIZE_X);
        int maxChunkX = Math.floorDiv(maxX, Chunk.CHUNK_SIZE_X);
        int minChunkZ = Math.floorDiv(minZ, Chunk.CHUNK_SIZE_Z);
        int maxChunkZ = Math.floorDiv(maxZ, Chunk.CHUNK_SIZE_Z);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Chunk chunk = instance.loadChunk(chunkX, chunkZ).join();
                if (chunk == null) {
                    continue;
                }
                applyChunkBatch(chunk, minX, maxX, minZ, maxZ, surfaceY, surfaceBlock, fillBlock);
            }
        }
    }

    private void applyChunkBatch(Chunk chunk,
                                 int minX, int maxX,
                                 int minZ, int maxZ,
                                 int surfaceY,
                                 Block surfaceBlock,
                                 Block fillBlock) {
        int chunkWorldX = chunk.getChunkX() << 4;
        int chunkWorldZ = chunk.getChunkZ() << 4;

        int startX = Math.max(minX, chunkWorldX);
        int endX = Math.min(maxX, chunkWorldX + Chunk.CHUNK_SIZE_X - 1);
        if (startX > endX) {
            return;
        }

        int startZ = Math.max(minZ, chunkWorldZ);
        int endZ = Math.min(maxZ, chunkWorldZ + Chunk.CHUNK_SIZE_Z - 1);
        if (startZ > endZ) {
            return;
        }

        ChunkBatch batch = new ChunkBatch(batchOption);
        boolean placeBelow = surfaceY - 1 >= MIN_SURFACE_Y;
        Block resolvedFill = Objects.requireNonNullElse(fillBlock, Block.AIR);

        for (int worldX = startX; worldX <= endX; worldX++) {
            int localX = worldX - chunkWorldX;
            for (int worldZ = startZ; worldZ <= endZ; worldZ++) {
                int localZ = worldZ - chunkWorldZ;
                batch.setBlock(localX, surfaceY, localZ, surfaceBlock);
                if (placeBelow) {
                    batch.setBlock(localX, surfaceY - 1, localZ, resolvedFill);
                }
            }
        }

        batch.apply(instance, chunk, NOOP_CALLBACK);
        batch.awaitReady();
    }

    private static int computeMinCoord(int size) {
        int half = size / 2;
        return -half;
    }

    private static int computeMaxCoord(int size, int minCoord) {
        return minCoord + size - 1;
    }

    private static int clampSize(int size) {
        return Math.max(1, Math.min(MAX_SIZE, size));
    }

    private static int clampHeight(int height) {
        int clamped = Math.max(MIN_SURFACE_Y, Math.min(MAX_SURFACE_Y, height));
        return clamped;
    }

    private static int intProperty(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static Block blockProperty(Object raw, String fallbackId) {
        String namespace = raw != null ? raw.toString() : fallbackId;
        Block block = Block.fromNamespaceId(namespace);
        if (block == null) {
            block = Block.fromNamespaceId(fallbackId);
        }
        return block != null ? block : Block.AIR;
    }
}
