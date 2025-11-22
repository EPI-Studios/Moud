package com.moud.server.instance;

import com.moud.server.editor.SceneDefaults;
import com.moud.server.logging.MoudLogger;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.SharedInstance;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.tag.Tag;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class InstanceManager {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(InstanceManager.class);
    private static InstanceManager instance;

    private final net.minestom.server.instance.InstanceManager minestomInstanceManager;
    private final Map<String, Instance> namedInstances;
    private final Map<UUID, Instance> instanceRegistry;
    private InstanceContainer defaultInstance;
    private InstanceContainer limboInstance;
    public static final Tag<Pos> SPAWN_TAG = Tag.Transient("moud:spawn");
    private static final Pos FALLBACK_SPAWN = new Pos(0.5, 66, 0.5);
    private static final int CHUNK_SIZE = 16;
    private static final int DEFAULT_WORLD_RADIUS_CHUNKS =
            (int) Math.ceil((SceneDefaults.BASE_SCENE_SIZE_BLOCKS / 2.0) / (double) CHUNK_SIZE);

    private InstanceManager() {
        this.minestomInstanceManager = MinecraftServer.getInstanceManager();
        this.namedInstances = new ConcurrentHashMap<>();
        this.instanceRegistry = new ConcurrentHashMap<>();
    }

    public static synchronized InstanceManager getInstance() {
        if (instance == null) {
            instance = new InstanceManager();
        }
        return instance;
    }

    public void initialize() {
        LOGGER.info("Initializing instance manager");
        createDefaultInstance();
        createLimboInstance();
        registerSpawnListener();
    }

    private void registerSpawnListener() {

        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(limboInstance);
        });

        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent.class, event -> {
            if (event.getSpawnInstance() == limboInstance) {
                final Player player = event.getPlayer();
                LOGGER.debug("Player {} spawned in limbo, transferring to default instance", player.getUsername());

                InstanceContainer realInstance = getDefaultInstance();
                Pos spawnPosition = realInstance.getTag(SPAWN_TAG);

                if (spawnPosition == null) {
                    LOGGER.warn("No spawn position set for default instance, using fallback: {}", FALLBACK_SPAWN);
                    spawnPosition = FALLBACK_SPAWN;
                } else {
                    LOGGER.debug("Spawning player {} at configured position: {}", player.getUsername(), spawnPosition);
                }

                Pos finalSpawnPosition = spawnPosition;
                player.setInstance(realInstance, spawnPosition).thenRun(() -> {
                    player.setRespawnPoint(finalSpawnPosition);
                    LOGGER.info("Player {} spawned at position: {}", player.getUsername(), finalSpawnPosition);
                });
            }
        });
    }

    private void createDefaultInstance() {
        if (defaultInstance == null) {
            defaultInstance = minestomInstanceManager.createInstanceContainer();
            defaultInstance.setChunkSupplier(LightingChunk::new);
            defaultInstance.setGenerator(unit -> {});
            defaultInstance.setTag(SPAWN_TAG, new Pos(0.5, SceneDefaults.defaultSpawnY(), 0.5));
            namedInstances.put("default", defaultInstance);
            instanceRegistry.put(defaultInstance.getUniqueId(), defaultInstance);
            LOGGER.info("Default instance created with flat world generator");
            preloadBaseTerrain(defaultInstance);
        }
    }

    private void createLimboInstance() {
        if (limboInstance == null) {
            limboInstance = minestomInstanceManager.createInstanceContainer();

            limboInstance.setGenerator(unit -> {});
            instanceRegistry.put(limboInstance.getUniqueId(), limboInstance);
            LOGGER.info("Limbo instance created for safe player spawning");
        }
    }

    public InstanceContainer getDefaultInstance() {
        if (defaultInstance == null) {
            createDefaultInstance();
        }
        return defaultInstance;
    }

    public InstanceContainer createInstance(String name) {
        if (namedInstances.containsKey(name)) {
            LOGGER.warn("Instance with name '{}' already exists", name);
            Instance existing = namedInstances.get(name);
            if (existing instanceof InstanceContainer) {
                return (InstanceContainer) existing;
            }
            throw new IllegalStateException("Instance exists but is not a container");
        }

        InstanceContainer instance = minestomInstanceManager.createInstanceContainer();
        namedInstances.put(name, instance);
        instanceRegistry.put(instance.getUniqueId(), instance);
        LOGGER.info("Created instance: {}", name);
        return instance;
    }

    public SharedInstance createSharedInstance(String name, InstanceContainer parent) {
        if (namedInstances.containsKey(name)) {
            LOGGER.warn("Instance with name '{}' already exists", name);
            Instance existing = namedInstances.get(name);
            if (existing instanceof SharedInstance) {
                return (SharedInstance) existing;
            }
            throw new IllegalStateException("Instance exists but is not a SharedInstance");
        }

        SharedInstance sharedInstance = minestomInstanceManager.createSharedInstance(parent);
        namedInstances.put(name, sharedInstance);
        instanceRegistry.put(sharedInstance.getUniqueId(), sharedInstance);
        LOGGER.info("Created shared instance: {} from parent instance", name);
        return sharedInstance;
    }

    public InstanceContainer loadWorld(String name, Path worldPath) {
        if (namedInstances.containsKey(name)) {
            LOGGER.warn("Instance with name '{}' already exists, cannot load world", name);
            Instance existing = namedInstances.get(name);
            if (existing instanceof InstanceContainer) {
                return (InstanceContainer) existing;
            }
            throw new IllegalStateException("Instance exists but is not a container");
        }

        InstanceContainer instance = minestomInstanceManager.createInstanceContainer();
        instance.setChunkLoader(new SafeAnvilLoader(worldPath));

        int loadedChunks = 0;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                try {
                    instance.loadChunk(x, z).join();
                    loadedChunks++;
                } catch (Exception e) {
                    LOGGER.warn("Failed to load chunk ({}, {}): {}", x, z, e.getMessage());
                }
            }
        }

        namedInstances.put(name, instance);
        instanceRegistry.put(instance.getUniqueId(), instance);
        LOGGER.info("Loaded world from {} into instance: {} ({}/25 chunks loaded)", worldPath, name, loadedChunks);
        return instance;
    }

    public void saveInstance(String name) {
        Instance instance = namedInstances.get(name);
        if (instance == null) {
            LOGGER.warn("Cannot save instance '{}': not found", name);
            return;
        }

        if (!(instance instanceof InstanceContainer)) {
            LOGGER.warn("Cannot save instance '{}': not a container instance", name);
            return;
        }

        InstanceContainer container = (InstanceContainer) instance;
        container.saveChunksToStorage().thenRun(() ->
                LOGGER.success("Instance '{}' saved successfully", name)
        ).exceptionally(throwable -> {
            LOGGER.error("Failed to save instance '{}'", name, throwable);
            return null;
        });
    }

    public void saveAllInstances() {
        LOGGER.info("Saving all instances...");
        for (Map.Entry<String, Instance> entry : namedInstances.entrySet()) {
            saveInstance(entry.getKey());
        }
    }

    public Instance getInstanceByName(String name) {
        return namedInstances.get(name);
    }

    public InstanceContainer getInstance(String name) {
        Instance instance = namedInstances.get(name);
        if (instance instanceof InstanceContainer) {
            return (InstanceContainer) instance;
        }
        return null;
    }

    public Instance getInstance(UUID uuid) {
        return instanceRegistry.get(uuid);
    }

    public boolean hasInstance(String name) {
        return namedInstances.containsKey(name);
    }

    public void unregisterInstance(String name) {
        Instance instance = namedInstances.remove(name);
        if (instance != null) {
            instanceRegistry.remove(instance.getUniqueId());
            minestomInstanceManager.unregisterInstance(instance);
            LOGGER.info("Unregistered instance: {}", name);
        }
    }

    public void setDefaultInstance(String name) {
        Instance instance = namedInstances.get(name);
        if (instance instanceof InstanceContainer) {
            this.defaultInstance = (InstanceContainer) instance;
            LOGGER.info("Default instance set to: {}", name);
        } else {
            LOGGER.warn("Cannot set default instance: '{}' is not a container instance", name);
        }
    }

    public Map<String, Instance> getAllNamedInstances() {
        return Map.copyOf(namedInstances);
    }

    public void shutdown() {
        LOGGER.info("Shutting down instance manager");
        saveAllInstances();
        namedInstances.clear();
        instanceRegistry.clear();
    }

    private void preloadBaseTerrain(InstanceContainer instance) {
        int diameterChunks = DEFAULT_WORLD_RADIUS_CHUNKS * 2 + 1;
        int chunkCount = diameterChunks * diameterChunks;
        LOGGER.info("Preloading base terrain area (~{}x{} blocks, {} chunks)",
                diameterChunks * CHUNK_SIZE,
                diameterChunks * CHUNK_SIZE,
                chunkCount);

        List<CompletableFuture<Chunk>> futures = new ArrayList<>(chunkCount);
        ChunkRange.chunksInRange(0, 0, DEFAULT_WORLD_RADIUS_CHUNKS,
                (chunkX, chunkZ) -> futures.add(instance.loadChunk(chunkX, chunkZ)));

        if (futures.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        CompletableFuture<?>[] tasks = futures.toArray(CompletableFuture<?>[]::new);
        CompletableFuture
                .allOf(tasks)
                .thenRunAsync(() -> {
                    LightingChunk.relight(instance, instance.getChunks());
                    long elapsed = System.currentTimeMillis() - startTime;
                    LOGGER.success("Base terrain prepared ({} chunks) in {} ms", chunkCount, elapsed);
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to preload base terrain chunks", ex);
                    return null;
                });
    }
}
