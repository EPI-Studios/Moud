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
import java.util.concurrent.atomic.AtomicReference;

public final class TerrainRuntimeAdapter implements SceneRuntimeAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerrainRuntimeAdapter.class);
    private static final ChunkCallback NOOP_CALLBACK = chunk -> {};
    private static final int MAX_SIZE = 4096;
    private static final int MIN_SURFACE_Y = 1;
    private static final int MAX_SURFACE_Y = 255;

    private final String sceneId;
    private final InstanceContainer instance;
    private final BatchOption batchOption;

    private final AtomicReference<TerrainSettings> currentSettings = new AtomicReference<>();

    public TerrainRuntimeAdapter(String sceneId) {
        this.sceneId = sceneId;
        this.instance = InstanceManager.getInstance().getDefaultInstance();
        this.batchOption = new BatchOption()
                .setCalculateInverse(false)
                .setSendUpdate(true)
                .setFullChunk(false)
                .setUnsafeApply(false);

        net.minestom.server.MinecraftServer.getGlobalEventHandler().addListener(
                net.minestom.server.event.instance.InstanceChunkLoadEvent.class,
                event -> {
                    if (event.getInstance() != instance) {
                        return;
                    }
                    TerrainSettings settings = currentSettings.get();
                    if (settings == null || !settings.intersects(event.getChunk())) {
                        return;
                    }
                    applyChunkBatch(event.getChunk(), settings);
                }
        );
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
        TerrainSettings settings = currentSettings.getAndSet(null);
        if (settings != null) {
            clearLoadedChunks(settings);
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

        TerrainSettings newSettings = new TerrainSettings(size, surfaceY, surfaceBlock, fillBlock);
        TerrainSettings previous = currentSettings.getAndSet(newSettings);
        if (newSettings.equals(previous)) {
            return;
        }

        long start = System.currentTimeMillis();
        refreshLoadedChunks(previous, newSettings);
        long duration = System.currentTimeMillis() - start;

        LOGGER.info("Scene '{}' terrain settings applied ({}x{} @ y={}) in {} ms (loaded chunks only)",
                sceneId, size, size, surfaceY, duration);
    }

    private void refreshLoadedChunks(TerrainSettings previous, TerrainSettings settings) {
        if (instance.getChunks().isEmpty()) {
            return;
        }
        instance.getChunks().forEach(chunk -> {
            boolean inNewArea = settings.intersects(chunk);
            boolean inOldArea = previous != null && previous.intersects(chunk);

            if (inNewArea) {
                applyChunkBatch(chunk, settings);
            } else if (inOldArea) {
                clearChunkArea(chunk, previous);
            }
        });
    }

    private void clearLoadedChunks(TerrainSettings settings) {
        instance.getChunks().forEach(chunk -> clearChunkArea(chunk, settings));
    }

    private void applyChunkBatch(Chunk chunk, TerrainSettings settings) {
        applyChunkBatch(chunk, settings.minCoord(), settings.maxCoord(), settings.minCoord(), settings.maxCoord(),
                settings.surfaceY(), settings.surfaceBlock(), settings.fillBlock());
    }

    private void clearChunkArea(Chunk chunk, TerrainSettings settings) {
        applyChunkBatch(chunk, settings.minCoord(), settings.maxCoord(), settings.minCoord(), settings.maxCoord(),
                settings.surfaceY(), Block.AIR, Block.AIR);
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

    private record TerrainSettings(int size, int surfaceY, Block surfaceBlock, Block fillBlock) {
        int minCoord() {
            return computeMinCoord(size);
        }

        int maxCoord() {
            return computeMaxCoord(size, minCoord());
        }

        boolean intersects(Chunk chunk) {
            int chunkWorldX = chunk.getChunkX() << 4;
            int chunkWorldZ = chunk.getChunkZ() << 4;
            int startX = Math.max(minCoord(), chunkWorldX);
            int endX = Math.min(maxCoord(), chunkWorldX + Chunk.CHUNK_SIZE_X - 1);
            int startZ = Math.max(minCoord(), chunkWorldZ);
            int endZ = Math.min(maxCoord(), chunkWorldZ + Chunk.CHUNK_SIZE_Z - 1);
            return startX <= endX && startZ <= endZ;
        }
    }
}
