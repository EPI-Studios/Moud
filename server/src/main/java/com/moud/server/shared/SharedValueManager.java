package com.moud.server.shared;

import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.shared.core.SharedValueStore;
import com.moud.server.shared.diagnostics.SharedStoreSnapshot;
import com.moud.server.shared.network.SharedValuePacketHandler;
import com.moud.server.shared.sync.ValueSynchronizer;
import net.minestom.server.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SharedValueManager {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            SharedValueManager.class,
            LogContext.builder().put("subsystem", "shared-values").build()
    );
    private static SharedValueManager instance;

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, SharedValueStore>> playerStores;
    private final ValueSynchronizer synchronizer;
    private final SharedValuePacketHandler packetHandler;

    private SharedValueManager() {
        this.playerStores = new ConcurrentHashMap<>();
        this.synchronizer = new ValueSynchronizer(this);
        this.packetHandler = new SharedValuePacketHandler(this);
    }

    public static synchronized SharedValueManager getInstance() {
        if (instance == null) {
            instance = new SharedValueManager();
        }
        return instance;
    }

    public SharedValueStore getOrCreateStore(Player player, String storeName) {
        String playerId = player.getUuid().toString();
        return playerStores
                .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(storeName, k -> new SharedValueStore(storeName, playerId));
    }

    public SharedValueStore getStore(Player player, String storeName) {
        String playerId = player.getUuid().toString();
        Map<String, SharedValueStore> stores = playerStores.get(playerId);
        return stores != null ? stores.get(storeName) : null;
    }

    public void removePlayer(Player player) {
        String playerId = player.getUuid().toString();
        Map<String, SharedValueStore> stores = playerStores.remove(playerId);
        if (stores != null) {
            stores.values().forEach(SharedValueStore::clear);
            LOGGER.debug(LogContext.builder()
                    .put("player", player.getUsername())
                    .put("player_uuid", playerId)
                    .build(), "Removed shared values for player: {}", player.getUsername());
        }
    }

    public boolean handleClientUpdate(Player player, String storeName, String key, Object value) {
        SharedValueStore store = getStore(player, storeName);
        if (store == null) {
            LOGGER.warn(LogContext.builder()
                    .put("player", player.getUsername())
                    .put("player_uuid", player.getUuid())
                    .put("store", storeName)
                    .put("key", key)
                    .build(), "Store '{}' not found for player: {}", storeName, player.getUsername());
            return false;
        }

        boolean success = store.updateFromClient(key, value);
        if (success) {
            synchronizer.syncImmediate(player, store, Map.of(key, value));
            LOGGER.debug(LogContext.builder()
                    .put("player", player.getUsername())
                    .put("player_uuid", player.getUuid())
                    .put("store", storeName)
                    .put("key", key)
                    .build(), "Client update accepted: {}.{} = {} for {}", storeName, key, value, player.getUsername());
        } else {
            LOGGER.warn(LogContext.builder()
                    .put("player", player.getUsername())
                    .put("player_uuid", player.getUuid())
                    .put("store", storeName)
                    .put("key", key)
                    .build(), "Client update rejected: {}.{} for {}", storeName, key, player.getUsername());
        }
        return success;
    }

    public void syncPlayerStore(Player player, SharedValueStore store) {
        synchronizer.requestSync(player, store);
    }

    public ValueSynchronizer getSynchronizer() {
        return synchronizer;
    }

    public SharedValuePacketHandler getPacketHandler() {
        return packetHandler;
    }

    public void initialize() {
        synchronizer.initialize();
        packetHandler.initialize();
        LOGGER.info("SharedValueManager initialized");
    }

    public void shutdown() {
        synchronizer.shutdown();
        playerStores.clear();
        LOGGER.info("SharedValueManager shutdown");
    }

    public List<SharedStoreSnapshot> snapshotAllStores() {
        List<SharedStoreSnapshot> snapshots = new ArrayList<>();
        playerStores.forEach((playerId, stores) ->
                stores.values().forEach(store -> snapshots.add(store.snapshot()))
        );
        return snapshots;
    }

    public List<SharedStoreSnapshot> snapshotStoresForPlayer(String playerId) {
        Map<String, SharedValueStore> stores = playerStores.get(playerId);
        if (stores == null) {
            return Collections.emptyList();
        }
        return stores.values().stream()
                .map(SharedValueStore::snapshot)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public SharedStoreSnapshot snapshotStore(String playerId, String storeName) {
        Map<String, SharedValueStore> stores = playerStores.get(playerId);
        if (stores == null) {
            return null;
        }
        SharedValueStore store = stores.get(storeName);
        return store != null ? store.snapshot() : null;
    }
}
