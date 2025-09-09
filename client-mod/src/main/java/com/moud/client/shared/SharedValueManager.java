package com.moud.client.shared;

import com.moud.client.shared.core.ClientValueCache;
import com.moud.client.shared.network.ClientPacketSender;
import com.moud.client.network.packets.ServerSyncValuePacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public class SharedValueManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SharedValueManager.class);
    private static SharedValueManager instance;

    private final ConcurrentHashMap<String, ClientValueCache> stores;
    private final ClientPacketSender packetSender;

    private SharedValueManager() {
        this.stores = new ConcurrentHashMap<>();
        this.packetSender = new ClientPacketSender();
    }

    public static synchronized SharedValueManager getInstance() {
        if (instance == null) {
            instance = new SharedValueManager();
        }
        return instance;
    }

    public ClientValueCache getOrCreateStore(String storeName) {
        return stores.computeIfAbsent(storeName, ClientValueCache::new);
    }

    public ClientValueCache getStore(String storeName) {
        return stores.get(storeName);
    }

    public void handleServerSync(ServerSyncValuePacket packet) {
        String storeName = packet.storeName();
        ClientValueCache store = getOrCreateStore(storeName);

        for (var entry : packet.deltaChanges().entrySet()) {
            store.updateFromServer(entry.getKey(), entry.getValue());
        }

        LOGGER.debug("Applied {} changes to store '{}' from server",
                packet.deltaChanges().size(), storeName);
    }

    public boolean requestUpdate(String storeName, String key, Object value) {
        ClientValueCache store = getStore(storeName);
        if (store == null || !store.canModify(key)) {
            return false;
        }

        store.updateOptimistic(key, value);
        packetSender.sendUpdate(storeName, key, value);
        return true;
    }

    public void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(
                ServerSyncValuePacket.ID,
                this::handleSyncPacket
        );
        LOGGER.info("SharedValueManager initialized");
    }

    public void cleanup() {
        stores.clear();
        LOGGER.info("SharedValueManager cleaned up");
    }

    private void handleSyncPacket(ServerSyncValuePacket packet, ClientPlayNetworking.Context context) {
        handleServerSync(packet);
    }
}