package com.moud.server.shared.sync;

import com.moud.network.MoudPackets;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.ServerPacketWrapper;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.shared.SharedValueManager;
import com.moud.server.shared.core.SharedValueStore;
import net.minestom.server.entity.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;

public class ValueSynchronizer {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            ValueSynchronizer.class,
            LogContext.builder().put("subsystem", "shared-values").put("component", "synchronizer").build()
    );
    private static final int BATCH_INTERVAL_MS = 50;

    private final SharedValueManager manager;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, SharedValueStore>> pendingBatchSync;
    private volatile boolean running;

    public ValueSynchronizer(SharedValueManager manager) {
        this.manager = manager;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SharedValue-Synchronizer");
            t.setDaemon(true);
            return t;
        });
        this.pendingBatchSync = new ConcurrentHashMap<>();
        this.running = false;
    }

    public void initialize() {
        running = true;
        scheduler.scheduleAtFixedRate(this::processBatchedSync, BATCH_INTERVAL_MS, BATCH_INTERVAL_MS, TimeUnit.MILLISECONDS);
        LOGGER.debug("ValueSynchronizer initialized with {}ms batch interval", BATCH_INTERVAL_MS);
    }

    public void shutdown() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
        LOGGER.debug("ValueSynchronizer shutdown");
    }

    public void requestSync(Player player, SharedValueStore store) {
        if (!running) return;

        String playerId = player.getUuid().toString();
        pendingBatchSync
                .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(store.getStoreName(), store);
    }

    public void syncImmediate(Player player, SharedValueStore store, Map<String, Object> changes) {
        if (!running) return;

        String playerId = player.getUuid().toString();
        long timestamp = System.currentTimeMillis();

        MoudPackets.SyncSharedValuesPacket packet = new MoudPackets.SyncSharedValuesPacket(
                playerId, store.getStoreName(), changes, timestamp
        );

        ServerNetworkManager manager = ServerNetworkManager.getInstance();
        if (manager != null) {
            manager.send(player, packet);
        }
        store.markAllClean();

        LOGGER.debug(LogContext.builder()
                        .put("player", player.getUsername())
                        .put("player_uuid", player.getUuid())
                        .put("store", store.getStoreName())
                        .put("changes", changes.size())
                        .build(),
                "Immediate sync sent to {}: {} changes in store '{}'",
                player.getUsername(), changes.size(), store.getStoreName());
    }

    private void processBatchedSync() {
        if (!running) return;

        pendingBatchSync.forEach((playerId, stores) -> {
            Player player = getPlayerById(playerId);
            if (player == null) {
                pendingBatchSync.remove(playerId);
                return;
            }

            stores.forEach((storeName, store) -> {
                Map<String, Object> dirtyValues = store.getDirtyValues();
                if (!dirtyValues.isEmpty()) {
                    long timestamp = System.currentTimeMillis();

                    MoudPackets.SyncSharedValuesPacket packet = new MoudPackets.SyncSharedValuesPacket(
                            playerId, storeName, dirtyValues, timestamp
                    );

                    ServerNetworkManager manager = ServerNetworkManager.getInstance();
                    if (manager != null) {
                        manager.send(player, packet);
                    }
                    store.markAllClean();

                    LOGGER.debug(LogContext.builder()
                                    .put("player", player.getUsername())
                                    .put("player_uuid", player.getUuid())
                                    .put("store", storeName)
                                    .put("changes", dirtyValues.size())
                                    .build(),
                            "Batched sync sent to {}: {} changes in store '{}'",
                            player.getUsername(), dirtyValues.size(), storeName);
                }
            });
        });

        pendingBatchSync.clear();
    }

    private Player getPlayerById(String playerId) {
        try {
            java.util.UUID uuid = java.util.UUID.fromString(playerId);
            return net.minestom.server.MinecraftServer.getConnectionManager()
                    .getOnlinePlayers()
                    .stream()
                    .filter(p -> p.getUuid().equals(uuid))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
