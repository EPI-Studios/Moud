package com.moud.server.instance;

import com.moud.server.editor.SceneDefaults;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.physics.PhysicsService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.SharedInstance;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InstanceManager {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(InstanceManager.class);
    private static InstanceManager instance;

    private final net.minestom.server.instance.InstanceManager minestomInstanceManager;
    private final Map<String, Instance> namedInstances;
    private final Map<UUID, Instance> instanceRegistry;
    private final Path projectRoot;
    private final SceneTerrainGenerator terrainGenerator = new SceneTerrainGenerator();
    private InstanceContainer defaultInstance;
    private InstanceContainer limboInstance;
    private Task autosaveTask;
    private final AtomicBoolean autosaveInProgress = new AtomicBoolean(false);
    private final AtomicBoolean autosavePending = new AtomicBoolean(false);
    public static final Tag<Pos> SPAWN_TAG = Tag.Transient("moud:spawn");
    private static final Pos FALLBACK_SPAWN = new Pos(0.5, 66, 0.5);
    private static final long NON_MOUD_LIMBO_GRACE_MILLIS = 2_000L;
    private static final long MOUD_LIMBO_MAX_WAIT_MILLIS = 120_000L;

    public static synchronized void install(InstanceManager instanceManager) {
        instance = Objects.requireNonNull(instanceManager, "instanceManager");
    }

    public InstanceManager(Path projectRoot) {
        this.minestomInstanceManager = MinecraftServer.getInstanceManager();
        this.namedInstances = new ConcurrentHashMap<>();
        this.instanceRegistry = new ConcurrentHashMap<>();
        this.projectRoot = projectRoot;
    }

    public InstanceManager() {
        this(null);
    }

    public static synchronized InstanceManager getInstance() {
        if (instance == null) {
            instance = new InstanceManager(null);
        }
        return instance;
    }

    public void initialize() {
        LOGGER.info("Initializing instance manager");
        createDefaultInstance();
        createLimboInstance();
        registerSpawnListener();
        int preloadRadius = Integer.parseInt(System.getProperty("moud.chunk.preloadRadius", "3"));
        new ChunkPreloader(preloadRadius).register();
        configureAutosave();
        configurePeriodicBackups();
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
                long spawnStart = System.currentTimeMillis();

                final Task[] gateTask = new Task[1];
                gateTask[0] = MinecraftServer.getSchedulerManager()
                        .buildTask(() -> {
                            if (!player.isOnline()) {
                                gateTask[0].cancel();
                                return;
                            }
                            if (player.getInstance() != limboInstance) {
                                gateTask[0].cancel();
                                return;
                            }

                            ServerNetworkManager networkManager = ServerNetworkManager.getInstance();
                            boolean isMoudClient = networkManager != null && networkManager.isMoudClient(player);
                            boolean clientReady = isMoudClient && networkManager.isClientReady(player);
                            long elapsedMillis = System.currentTimeMillis() - spawnStart;

                            if (clientReady) {
                                gateTask[0].cancel();
                                player.setInstance(realInstance, finalSpawnPosition).thenRun(() -> {
                                    player.setRespawnPoint(finalSpawnPosition);
                                    LOGGER.info("Player {} released from limbo (client ready)", player.getUsername());
                                });
                                return;
                            }

                            if (!isMoudClient && elapsedMillis >= NON_MOUD_LIMBO_GRACE_MILLIS) {
                                gateTask[0].cancel();
                                player.setInstance(realInstance, finalSpawnPosition).thenRun(() -> {
                                    player.setRespawnPoint(finalSpawnPosition);
                                    LOGGER.info("Player {} released from limbo (non-Moud)", player.getUsername());
                                });
                                return;
                            }

                            if (isMoudClient && elapsedMillis >= MOUD_LIMBO_MAX_WAIT_MILLIS) {
                                gateTask[0].cancel();
                                player.setInstance(realInstance, finalSpawnPosition).thenRun(() -> {
                                    player.setRespawnPoint(finalSpawnPosition);
                                    LOGGER.warn("Player {} released from limbo after timeout", player.getUsername());
                                });
                            }
                        })
                        .repeat(TaskSchedule.tick(5))
                        .schedule();
            }
        });
    }

    private void createDefaultInstance() {
        if (defaultInstance == null) {
            defaultInstance = minestomInstanceManager.createInstanceContainer();
            defaultInstance.setChunkSupplier(LightingChunk::new);
            defaultInstance.setGenerator(terrainGenerator::generate);
            defaultInstance.setTag(SPAWN_TAG, new Pos(0.5, SceneDefaults.defaultSpawnY(), 0.5));
            configureDefaultChunkLoader(defaultInstance);
            namedInstances.put("default", defaultInstance);
            instanceRegistry.put(defaultInstance.getUniqueId(), defaultInstance);
            LOGGER.info("Default instance created with scene terrain generator");
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

    public void updateDefaultTerrain(SceneTerrainGenerator.TerrainSettings settings) {
        if (settings == null) {
            return;
        }
        terrainGenerator.setSettings(settings);
    }

    private void configureAutosave() {
        if (projectRoot == null) {
            return;
        }

        long autosaveSeconds;
        try {
            String raw = System.getProperty("moud.scene.autosaveSeconds");
            if (raw == null) {
                raw = System.getProperty("moud.world.autosaveSeconds", "60");
            }
            autosaveSeconds = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            autosaveSeconds = 60;
        }

        if (autosaveSeconds <= 0) {
            return;
        }

        autosaveTask = MinecraftServer.getSchedulerManager()
                .buildTask(this::autosaveDefaultInstance)
                .repeat(TaskSchedule.seconds(autosaveSeconds))
                .schedule();
        LOGGER.info("Default scene autosave enabled (every {}s)", autosaveSeconds);
    }

    private void configurePeriodicBackups() {
        long backupIntervalSeconds;
        try {
            String raw = System.getProperty("moud.backup.intervalSeconds", "3600");
            backupIntervalSeconds = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            backupIntervalSeconds = 3600;
        }

        if (backupIntervalSeconds <= 0) {
            LOGGER.info("Periodic backups disabled");
            return;
        }

        MinecraftServer.getSchedulerManager()
                .buildTask(this::createWorldBackup)
                .delay(TaskSchedule.seconds(backupIntervalSeconds))
                .repeat(TaskSchedule.seconds(backupIntervalSeconds))
                .schedule();

        LOGGER.info("Periodic world backups enabled (every {} seconds, ~{} minutes)",
                   backupIntervalSeconds, backupIntervalSeconds / 60);
    }

    private void autosaveDefaultInstance() {
        InstanceContainer instance = defaultInstance;
        if (instance == null) {
            return;
        }
        if (!autosaveInProgress.compareAndSet(false, true)) {
            autosavePending.set(true);
            return;
        }

        instance.saveChunksToStorage().whenComplete((ignored, throwable) -> {
            autosaveInProgress.set(false);
            if (throwable != null) {
                LOGGER.error("Default scene autosave failed", throwable);
            }
            if (autosavePending.getAndSet(false)) {
                autosaveDefaultInstance();
            }
        });
    }

    public void requestSceneSave(String sceneId) {
        if (sceneId == null) {
            return;
        }
        if (!SceneDefaults.DEFAULT_SCENE_ID.equals(sceneId)) {
            return;
        }
        autosaveDefaultInstance();
    }

    public Path createWorldBackup() {
        if (projectRoot == null) {
            LOGGER.warn("Cannot create backup: projectRoot is null");
            return null;
        }

        Path sceneWorldFile = projectRoot.resolve(".moud").resolve("scenes")
                .resolve(SceneDefaults.DEFAULT_SCENE_ID + ".polar");

        if (!Files.exists(sceneWorldFile)) {
            LOGGER.debug("No world file to backup: {}", sceneWorldFile);
            return null;
        }

        try {
            Path backupPath = createBackup(sceneWorldFile, "auto");
            LOGGER.info("Created automatic backup: {}", backupPath);
            return backupPath;
        } catch (IOException e) {
            LOGGER.error("Failed to create automatic backup of world file", e);
            return null;
        }
    }

    private void configureDefaultChunkLoader(InstanceContainer instance) {
        if (instance == null || projectRoot == null) {
            return;
        }

        Path sceneDirectory = projectRoot.resolve(".moud").resolve("scenes");
        Path sceneWorldFile = sceneDirectory.resolve(SceneDefaults.DEFAULT_SCENE_ID + ".polar");
        try {
            Files.createDirectories(sceneDirectory);
            SceneWorldAccess worldAccess = new SceneWorldAccess(SceneDefaults.DEFAULT_SCENE_ID, projectRoot);
            SceneWorldChunkLoader loader = new SceneWorldChunkLoader(sceneWorldFile, worldAccess);
            if (!ensureMigratedDefaultWorld(instance, sceneWorldFile, loader)) {
                instance.setChunkLoader(loader);
                loader.loadInstance(instance);
            }
            LOGGER.info("Default scene storage set to {}", sceneWorldFile);
        } catch (IOException e) {
            LOGGER.error("Failed to configure default scene storage at {}", sceneWorldFile, e);

            if (Files.exists(sceneWorldFile) && e.getMessage() != null && e.getMessage().contains("Corrupted or invalid")) {
                handleCorruptedWorldFile(instance, sceneWorldFile);
            }
        }
    }

    private void handleCorruptedWorldFile(InstanceContainer instance, Path sceneWorldFile) {
        System.err.println("\n" + "=".repeat(80));
        System.err.println("ERROR: Corrupted world file detected!");
        System.err.println("=".repeat(80));
        System.err.println("Location: " + sceneWorldFile);
        System.err.println("\nThe world file appears to be corrupted or empty and cannot be loaded.");
        System.err.println("This may have happened due to an improper shutdown.");

        Path backupPath = null;
        try {
            backupPath = createBackup(sceneWorldFile, "corrupted");
            System.err.println("\n✓ Corrupted file backed up to: " + backupPath);
        } catch (IOException e) {
            System.err.println("\n✗ Warning: Failed to create backup: " + e.getMessage());
            LOGGER.warn("Failed to backup corrupted world file", e);
        }

        System.err.println("\nYou have 2 choices:");
        System.err.println("  1. Delete the corrupted file and start with a fresh file (backup will be preserved)");
        System.err.println("  2. Exit and manually backup/recover the file");
        System.err.println("\nWould you like to delete the corrupted file and start fresh? (yes/no): ");

        try (Scanner scanner = new Scanner(System.in)) {
            String response = scanner.nextLine().trim().toLowerCase();

            if (response.equals("yes") || response.equals("y")) {
                try {
                    Files.deleteIfExists(sceneWorldFile);
                    LOGGER.warn("Deleted corrupted world file: {}", sceneWorldFile);

                    SceneWorldAccess worldAccess = new SceneWorldAccess(SceneDefaults.DEFAULT_SCENE_ID, projectRoot);
                    SceneWorldChunkLoader loader = new SceneWorldChunkLoader(sceneWorldFile, worldAccess);
                    if (!ensureMigratedDefaultWorld(instance, sceneWorldFile, loader)) {
                        instance.setChunkLoader(loader);
                        loader.loadInstance(instance);
                    }
                    System.out.println("✓ Successfully created fresh world file");
                    if (backupPath != null) {
                        System.out.println("✓ Original file backed up at: " + backupPath);
                    }
                    LOGGER.info("Successfully created fresh default scene storage at {}", sceneWorldFile);
                } catch (IOException retryError) {
                    System.err.println("✗ Failed to recover from corrupted world file");
                    LOGGER.error("Failed to recover from corrupted world file at {}", sceneWorldFile, retryError);
                    throw new RuntimeException("Cannot start server without a valid world file", retryError);
                }
            } else {
                System.err.println("\nServer startup cancelled.");
                if (backupPath != null) {
                    System.err.println("Backup location: " + backupPath);
                }
                System.err.println("\nTo recover manually:");
                System.err.println("  1. Inspect/recover the backup: " + (backupPath != null ? backupPath : sceneWorldFile));
                System.err.println("  2. Delete the corrupted file: rm \"" + sceneWorldFile + "\"");
                System.err.println("  3. Restart the server");
                throw new RuntimeException("Server startup cancelled by user due to corrupted world file");
            }
        }
    }

    private Path createBackup(Path sourceFile, String reason) throws IOException {
        if (!Files.exists(sourceFile)) {
            throw new IOException("Source file does not exist: " + sourceFile);
        }

        Path backupDir = projectRoot.resolve(".moud").resolve("backups");
        Files.createDirectories(backupDir);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String originalFilename = sourceFile.getFileName().toString();
        String backupFilename = originalFilename.replace(".polar", "_" + reason + "_" + timestamp + ".polar");

        Path backupPath = backupDir.resolve(backupFilename);

        Files.copy(sourceFile, backupPath, StandardCopyOption.REPLACE_EXISTING);

        LOGGER.info("Created backup: {} -> {}", sourceFile, backupPath);

        cleanupOldBackups(backupDir, 30);

        return backupPath;
    }

    private void cleanupOldBackups(Path backupDir, int maxBackups) {
        try {
            List<Path> backups = Files.list(backupDir)
                    .filter(p -> p.getFileName().toString().endsWith(".polar"))
                    .sorted((p1, p2) -> {
                        try {
                            return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .toList();

            if (backups.size() > maxBackups) {
                for (int i = maxBackups; i < backups.size(); i++) {
                    Files.deleteIfExists(backups.get(i));
                    LOGGER.debug("Deleted old backup: {}", backups.get(i));
                }
                LOGGER.info("Cleaned up {} old backups", backups.size() - maxBackups);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to cleanup old backups", e);
        }
    }

    private boolean ensureMigratedDefaultWorld(
            InstanceContainer instance,
            Path sceneWorldFile,
            SceneWorldChunkLoader worldLoader
    ) {
        if (instance == null || sceneWorldFile == null || Files.exists(sceneWorldFile) || projectRoot == null || worldLoader == null) {
            return false;
        }

        Path legacyWorldPath = projectRoot.resolve(".moud").resolve("worlds").resolve("default");
        if (!Files.isDirectory(legacyWorldPath)) {
            return false;
        }

        LOGGER.info("Migrating legacy Anvil scene world {} to {}", legacyWorldPath, sceneWorldFile);

        List<ChunkCoord> chunksToLoad = listAnvilChunks(legacyWorldPath);
        if (chunksToLoad.isEmpty()) {
            LOGGER.warn("Legacy Anvil world {} contains no region chunks; skipping migration", legacyWorldPath);
            return false;
        }

        try {
            instance.setChunkLoader(new SafeAnvilLoader(legacyWorldPath));

            int loadedChunks = 0;
            for (ChunkCoord coord : chunksToLoad) {
                try {
                    if (instance.loadChunk(coord.x(), coord.z()).join() != null) {
                        loadedChunks++;
                    }
                } catch (Exception e) {
                    LOGGER.debug("Failed to load legacy chunk {}, {} during migration", coord.x(), coord.z(), e);
                }
            }

            instance.setChunkLoader(worldLoader);
            worldLoader.loadInstance(instance);
            instance.saveChunksToStorage().join();
            LOGGER.info(
                    "Migrated {} chunks from legacy Anvil world {} to {}",
                    loadedChunks,
                    legacyWorldPath,
                    sceneWorldFile
            );
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Failed to migrate legacy Anvil world {} to {}", legacyWorldPath, sceneWorldFile, t);
            return false;
        }
    }

    private static final Pattern REGION_FILE_PATTERN = Pattern.compile("^r\\.(?<x>-?\\d+)\\.(?<z>-?\\d+)\\.mca$");

    private static List<ChunkCoord> listAnvilChunks(Path anvilWorldPath) {
        if (anvilWorldPath == null) {
            return List.of();
        }
        Path regionDir = anvilWorldPath.resolve("region");
        if (!Files.isDirectory(regionDir)) {
            return List.of();
        }

        List<ChunkCoord> result = new ArrayList<>();
        try (var paths = Files.list(regionDir)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                String filename = path.getFileName().toString();
                Matcher matcher = REGION_FILE_PATTERN.matcher(filename);
                if (!matcher.matches()) {
                    return;
                }
                int regionX;
                int regionZ;
                try {
                    regionX = Integer.parseInt(matcher.group("x"));
                    regionZ = Integer.parseInt(matcher.group("z"));
                } catch (NumberFormatException ignored) {
                    return;
                }

                byte[] locations = readRegionLocations(path);
                if (locations == null || locations.length < 4096) {
                    return;
                }
                for (int i = 0; i < 1024; i++) {
                    int base = i * 4;
                    int offset = ((locations[base] & 0xFF) << 16)
                            | ((locations[base + 1] & 0xFF) << 8)
                            | (locations[base + 2] & 0xFF);
                    int sectors = locations[base + 3] & 0xFF;
                    if (offset == 0 || sectors == 0) {
                        continue;
                    }
                    int localX = i & 31;
                    int localZ = (i >> 5) & 31;
                    result.add(new ChunkCoord(regionX * 32 + localX, regionZ * 32 + localZ));
                }
            });
        } catch (IOException e) {
            return List.of();
        }

        return result;
    }

    private static byte[] readRegionLocations(Path regionFile) {
        try (FileChannel channel = FileChannel.open(regionFile, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            int read = channel.read(buffer);
            if (read <= 0) {
                return null;
            }
            return buffer.array();
        } catch (IOException ignored) {
            return null;
        }
    }

    private record ChunkCoord(int x, int z) {
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
        return loadWorld(name, worldPath, null);
    }

    public InstanceContainer loadWorld(String name, Path worldPath, String sceneId) {
        if (worldPath != null && isSceneWorldFile(worldPath)) {
            String resolvedSceneId = (sceneId == null || sceneId.isBlank())
                    ? guessSceneId(worldPath, name)
                    : sceneId;
            return loadSceneWorldFile(name, worldPath, resolvedSceneId);
        }

        if (worldPath != null && Files.isRegularFile(worldPath)) {
            throw new IllegalArgumentException("World path must be a directory or a .polar file: " + worldPath);
        }

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

    private InstanceContainer loadSceneWorldFile(String name, Path sceneWorldFile, String sceneId) {
        if (namedInstances.containsKey(name)) {
            LOGGER.warn("Instance with name '{}' already exists, cannot load world", name);
            Instance existing = namedInstances.get(name);
            if (existing instanceof InstanceContainer) {
                return (InstanceContainer) existing;
            }
            throw new IllegalStateException("Instance exists but is not a container");
        }

        if (sceneWorldFile == null) {
            throw new IllegalArgumentException("worldPath cannot be null");
        }

        InstanceContainer instance = minestomInstanceManager.createInstanceContainer();
        instance.setChunkSupplier(LightingChunk::new);
        instance.setTag(SPAWN_TAG, new Pos(0.5, SceneDefaults.defaultSpawnY(), 0.5));

        try {
            Path parent = sceneWorldFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            SceneWorldAccess worldAccess = new SceneWorldAccess(sceneId, projectRoot);
            SceneWorldChunkLoader loader = new SceneWorldChunkLoader(sceneWorldFile, worldAccess);
            instance.setChunkLoader(loader);
            loader.loadInstance(instance);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load world from " + sceneWorldFile, e);
        }

        namedInstances.put(name, instance);
        instanceRegistry.put(instance.getUniqueId(), instance);
        LOGGER.info("Loaded world from {} into instance '{}' (sceneId='{}')", sceneWorldFile, name, sceneId);
        return instance;
    }

    private static String guessSceneId(Path worldPath, String fallback) {
        if (worldPath == null) {
            return Objects.requireNonNullElse(fallback, "");
        }
        Path fileName = worldPath.getFileName();
        if (fileName == null) {
            return Objects.requireNonNullElse(fallback, "");
        }
        String filename = fileName.toString();
        if (filename.endsWith(".polar")) {
            String stem = filename.substring(0, filename.length() - ".polar".length());
            if (!stem.isBlank()) {
                return stem;
            }
        }
        return Objects.requireNonNullElse(fallback, "");
    }

    private static boolean isSceneWorldFile(Path path) {
        if (path == null) {
            return false;
        }
        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        return fileName.toString().endsWith(".polar");
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
            InstanceContainer previous = this.defaultInstance;
            this.defaultInstance = (InstanceContainer) instance;
            LOGGER.info("Default instance set to: {}", name);
            if (previous != this.defaultInstance) {
                PhysicsService.getInstance().onDefaultInstanceChanged(this.defaultInstance);
            }
        } else {
            LOGGER.warn("Cannot set default instance: '{}' is not a container instance", name);
        }
    }

    public Map<String, Instance> getAllNamedInstances() {
        return Map.copyOf(namedInstances);
    }

    private CompletableFuture<Void> saveAllInstancesToStorage() {
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        for (Map.Entry<String, Instance> entry : namedInstances.entrySet()) {
            Instance instance = entry.getValue();
            if (!(instance instanceof InstanceContainer container)) {
                continue;
            }
            saves.add(container.saveChunksToStorage());
        }

        if (saves.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(saves.toArray(new CompletableFuture[0]));
    }

    public void shutdown() {
        LOGGER.info("Shutting down instance manager");
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        try {
            saveAllInstancesToStorage().orTimeout(30, TimeUnit.SECONDS).join();
        } catch (RuntimeException e) {
            LOGGER.error("Failed to save instances during shutdown", e);
        }
        namedInstances.clear();
        instanceRegistry.clear();
    }
}
