package com.moud.server.physics.chunk;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.CollisionGroup;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.moud.api.math.Vector3;
import com.moud.server.instance.InstanceManager;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.physics.PhysicsService;
import com.moud.server.physics.mesh.ChunkMesher;
import com.moud.server.proxy.ModelProxy;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.instance.InstanceChunkLoadEvent;
import net.minestom.server.event.instance.InstanceChunkUnloadEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkPhysicsManager {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            ChunkPhysicsManager.class,
            LogContext.builder().put("subsystem", "physics").build()
    );

    private final PhysicsService service;
    private final ConcurrentHashMap<ChunkKey, Integer> chunkBodies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkKey, Long> pendingRefreshNs = new ConcurrentHashMap<>();

    public ChunkPhysicsManager(PhysicsService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public int getChunkBodyCount() {
        return chunkBodies.size();
    }

    public void registerEventHandlers() {
        var handler = MinecraftServer.getGlobalEventHandler();
        handler.addListener(InstanceChunkLoadEvent.class, event -> {
            Chunk chunk = event.getChunk();
            if (shouldHandleChunk(chunk)) {
                refreshChunk(chunk);
            }
        });
        handler.addListener(InstanceChunkUnloadEvent.class, event -> {
            Chunk chunk = event.getChunk();
            if (shouldHandleChunk(chunk)) {
                removeChunk(chunk);
            }
        });
        handler.addListener(PlayerBlockPlaceEvent.class, event -> {
            Chunk chunk = event.getInstance().getChunkAt(event.getBlockPosition());
            if (shouldHandleChunk(chunk)) {
                scheduleRefresh(chunk.getInstance(), chunk.getChunkX(), chunk.getChunkZ());
            }
        });
        handler.addListener(PlayerBlockBreakEvent.class, event -> {
            Chunk chunk = event.getInstance().getChunkAt(event.getBlockPosition());
            if (shouldHandleChunk(chunk)) {
                scheduleRefresh(chunk.getInstance(), chunk.getChunkX(), chunk.getChunkZ());
            }
        });
    }

    public void requestChunkRefresh(Instance instance, int chunkX, int chunkZ) {
        scheduleRefresh(instance, chunkX, chunkZ);
    }

    private void scheduleRefresh(Instance instance, int chunkX, int chunkZ) {
        if (instance == null) {
            return;
        }
        ChunkKey key = ChunkKey.from(instance, chunkX, chunkZ);
        long now = System.nanoTime();
        Long previous = pendingRefreshNs.putIfAbsent(key, now);
        if (previous != null) {
            pendingRefreshNs.put(key, now);
            return;
        }
        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> flushRefresh(key, instance, chunkX, chunkZ));
    }

    private void flushRefresh(ChunkKey key, Instance instance, int chunkX, int chunkZ) {
        pendingRefreshNs.remove(key);
        Chunk chunk = instance.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            instance.loadChunk(chunkX, chunkZ).thenAccept(loaded -> {
                if (loaded != null) {
                    refreshChunk(loaded);
                }
            });
            return;
        }
        refreshChunk(chunk);
    }

    public void primeInitialChunks() {
        primeChunksForInstance(InstanceManager.getInstance().getDefaultInstance());
    }

    public void onDefaultInstanceChanged(Instance instance) {
        if (instance == null) {
            return;
        }

        remeshAllChunksForInstance(instance);
        primeChunksForInstance(instance);
    }

    public void ensureChunksLoadedForPlayer(Player player) {
        if (player == null) {
            return;
        }
        ensureChunksLoadedForPlayer(player, player.getPosition());
    }

    public void ensureChunksLoadedForPlayer(Player player, Pos feetPosition) {
        if (player == null || feetPosition == null) {
            return;
        }
        Instance instance = player.getInstance();
        if (instance == null) {
            return;
        }

        int chunkX = feetPosition.chunkX();
        int chunkZ = feetPosition.chunkZ();

        LOGGER.info("Ensuring physics chunks for player at ({}, {})", chunkX, chunkZ);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = chunkX + dx;
                int cz = chunkZ + dz;
                Chunk chunk = instance.getChunk(cx, cz);

                if (chunk == null) {
                    LOGGER.info("Chunk ({}, {}) not loaded, forcing load...", cx, cz);
                    try {
                        chunk = instance.loadChunk(cx, cz).join();
                    } catch (Exception e) {
                        LOGGER.error("Failed to load chunk ({}, {})", cx, cz, e);
                        continue;
                    }
                }

                ChunkKey key = ChunkKey.from(chunk);
                if (!chunkBodies.containsKey(key)) {
                    LOGGER.info("Chunk ({}, {}) loaded but no physics body. Meshing now...", cx, cz);
                    refreshChunk(chunk);
                }
            }
        }
    }

    public void ensureChunksLoadedForPosition(ModelProxy model, Vector3 position) {
        if (model == null || position == null) {
            return;
        }
        EntityAndInstance resolved = resolveInstance(model);
        Instance instance = resolved.instance();
        if (instance == null) {
            return;
        }

        int chunkX = (int) Math.floor(position.x / 16.0);
        int chunkZ = (int) Math.floor(position.z / 16.0);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int targetX = chunkX + dx;
                int targetZ = chunkZ + dz;
                Chunk chunk = instance.getChunk(targetX, targetZ);
                if (chunk != null) {
                    refreshChunk(chunk);
                } else {
                    instance.loadChunk(targetX, targetZ).thenAccept(loadedChunk -> {
                        if (loadedChunk != null) {
                            refreshChunk(loadedChunk);
                        }
                    });
                }
            }
        }
    }

    public void refreshChunk(Chunk chunk) {
        if (!shouldHandleChunk(chunk)) {
            return;
        }

        BodyCreationSettings settings;
        try {
            boolean fullBlocksOnly = !service.isDefaultInstance(chunk.getInstance());
            settings = ChunkMesher.createChunk(chunk, fullBlocksOnly);
        } catch (Exception ex) {
            LOGGER.error("Chunk meshing exception", ex);
            return;
        }

        ChunkKey key = ChunkKey.from(chunk);
        CollisionGroup group = service.collisionGroupForInstance(chunk.getInstance());

        service.executeOnPhysicsThread(() -> {
            Integer oldBodyId = chunkBodies.remove(key);
            BodyInterface bi = service.getBodyInterface();

            if (oldBodyId != null) {
                bi.removeBody(oldBodyId);
                bi.destroyBody(oldBodyId);
            }

            if (settings == null) {
                LOGGER.warn(
                        "ChunkMesher returned null settings for chunk ({}, {}) - Empty or Air?",
                        chunk.getChunkX(),
                        chunk.getChunkZ()
                );
                return;
            }

            settings.setCollisionGroup(group);
            Body newBody = bi.createBody(settings);
            bi.addBody(newBody, EActivation.DontActivate);
            chunkBodies.put(key, newBody.getId());
        });
    }

    public void removeChunk(Chunk chunk) {
        if (!shouldHandleChunk(chunk)) {
            return;
        }

        ChunkKey key = ChunkKey.from(chunk);
        Integer bodyId = chunkBodies.remove(key);
        if (bodyId == null) {
            return;
        }

        service.executeOnPhysicsThread(() -> {
            BodyInterface bi = service.getBodyInterface();
            bi.removeBody(bodyId);
            bi.destroyBody(bodyId);
        });
    }

    private boolean shouldHandleChunk(Chunk chunk) {
        return chunk != null && service.shouldHandleInstance(chunk.getInstance());
    }

    private void primeChunksForInstance(Instance instance) {
        if (instance == null) {
            return;
        }
        int radius = Integer.parseInt(System.getProperty("moud.physics.chunkRadius", "2"));
        int totalChunks = (2 * radius + 1) * (2 * radius + 1);
        int loadedCount = 0;

        LOGGER.info("Pre-generating physics meshes for {} chunks (radius {})...", totalChunks, radius);
        long startTime = System.currentTimeMillis();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                try {
                    Chunk chunk = instance.loadChunk(x, z).join();
                    if (chunk != null) {
                        refreshChunk(chunk);
                        loadedCount++;
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to pre-load chunk ({}, {})", x, z, e);
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        LOGGER.info("Pre-generated physics meshes for {}/{} chunks in {}ms", loadedCount, totalChunks, elapsed);
    }

    private void remeshAllChunksForInstance(Instance instance) {
        UUID instanceId = instance.getUniqueId();
        if (instanceId == null) {
            instanceId = ChunkKey.FALLBACK_INSTANCE_ID;
        }

        int remeshedCount = 0;
        List<ChunkKey> keysToRemesh = new ArrayList<>();

        for (ChunkKey key : chunkBodies.keySet()) {
            if (key.instanceId().equals(instanceId)) {
                keysToRemesh.add(key);
            }
        }

        for (ChunkKey key : keysToRemesh) {
            Chunk chunk = instance.getChunk(key.chunkX(), key.chunkZ());
            if (chunk != null) {
                refreshChunk(chunk);
                remeshedCount++;
            }
        }

        LOGGER.info(
                LogContext.builder()
                        .put("instance_uuid", instanceId)
                        .put("remeshed_chunks", remeshedCount)
                        .build(),
                "Re-meshed {} existing chunk physics bodies for instance",
                remeshedCount
        );
    }

    private EntityAndInstance resolveInstance(ModelProxy model) {
        if (model == null) {
            return new EntityAndInstance(null, null);
        }
        var entity = model.getEntity();
        Instance instance = entity != null ? entity.getInstance() : null;
        return new EntityAndInstance(entity, instance);
    }

    private record EntityAndInstance(net.minestom.server.entity.Entity entity, Instance instance) {
    }
}
