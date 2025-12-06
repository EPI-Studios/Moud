package com.moud.server.instance;

import com.moud.server.logging.MoudLogger;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Eagerly loads a small chunk radius around players to avoid lazy chunk pop-in.
 */
public final class ChunkPreloader {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(ChunkPreloader.class);
    private final int radius;
    private final Map<java.util.UUID, Long> playerChunkCache = new ConcurrentHashMap<>();

    public ChunkPreloader(int radius) {
        this.radius = Math.max(1, radius);
    }

    public void register() {
        var handler = MinecraftServer.getGlobalEventHandler();
        handler.addListener(PlayerSpawnEvent.class, event -> {
            Player player = event.getPlayer();
            preloadAround(player, player.getPosition());
        });
        handler.addListener(PlayerMoveEvent.class, event -> {
            Player player = event.getPlayer();
            Pos newPos = event.getNewPosition();
            if (chunkKey(newPos) != playerChunkCache.getOrDefault(player.getUuid(), Long.MIN_VALUE)) {
                preloadAround(player, newPos);
            }
        });
        LOGGER.info("Chunk preloader registered with radius {} chunks", radius);
    }

    private void preloadAround(Player player, Pos pos) {
        Instance instance = player.getInstance();
        if (instance == null) return;

        int chunkX = (int) Math.floor(pos.x() / 16.0);
        int chunkZ = (int) Math.floor(pos.z() / 16.0);
        playerChunkCache.put(player.getUuid(), chunkKey(pos));

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int targetX = chunkX + dx;
                int targetZ = chunkZ + dz;
                if (instance.getChunk(targetX, targetZ) == null) {
                    try {
                        instance.loadChunk(targetX, targetZ).join();
                    } catch (Exception e) {
                        LOGGER.warn("Failed to pre-load chunk ({}, {}) for {}", targetX, targetZ, player.getUsername(), e);
                    }
                }
            }
        }
    }

    private long chunkKey(Pos pos) {
        int chunkX = (int) Math.floor(pos.x() / 16.0);
        int chunkZ = (int) Math.floor(pos.z() / 16.0);
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }
}
