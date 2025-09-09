package com.moud.server.shared;

import com.moud.server.shared.core.SharedValueStore;
import com.moud.server.shared.sync.ValueSynchronizer;
import com.moud.server.shared.network.SharedValuePacketHandler;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class SharedValueManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SharedValueManager.class);
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
            LOGGER.debug("Removed shared values for player: {}", player.getUsername());
        }
    }

    public boolean handleClientUpdate(Player player, String storeName, String key, Object value) {
        SharedValueStore store = getStore(player, storeName);
        if (store == null) {
            LOGGER.warn("Store '{}' not found for player: {}", storeName, player.getUsername());
            return false;
        }

        boolean success = store.updateFromClient(key, value);
        if (success) {
            synchronizer.syncImmediate(player, store, Map.of(key, value));
            LOGGER.debug("Client update accepted: {}.{} = {} for {}", storeName, key, value, player.getUsername());
        } else {
            LOGGER.warn("Client update rejected: {}.{} for {}", storeName, key, player.getUsername());
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
}