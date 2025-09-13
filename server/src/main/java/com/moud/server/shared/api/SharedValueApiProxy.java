package com.moud.server.shared.api;

import com.moud.server.shared.SharedValueManager;
import com.moud.server.shared.core.SharedValue;
import com.moud.server.shared.core.SharedValueStore;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public class SharedValueApiProxy {
    private final Player player;
    private final SharedValueManager manager;

    public SharedValueApiProxy(Player player) {
        this.player = player;
        this.manager = SharedValueManager.getInstance();
    }

    @HostAccess.Export
    public SharedStoreProxy getStore(String storeName) {
        SharedValueStore store = manager.getOrCreateStore(player, storeName);
        return new SharedStoreProxy(store, manager, player);
    }

    public static class SharedStoreProxy {
        private final SharedValueStore store;
        private final SharedValueManager manager;
        private final Player player;

        public SharedStoreProxy(SharedValueStore store, SharedValueManager manager, Player player) {
            this.store = store;
            this.manager = manager;
            this.player = player;
        }

        @HostAccess.Export
        public void set(String key, Object value, String syncMode, String permission) {
            SharedValue.SyncMode mode = parseSyncMode(syncMode);
            SharedValue.Permission perm = parsePermission(permission);
            store.set(key, value, perm, mode);
            if (mode == SharedValue.SyncMode.IMMEDIATE) {
                manager.getSynchronizer().syncImmediate(player, store, java.util.Map.of(key, value));
            } else {
                manager.syncPlayerStore(player, store);
            }
        }

        @HostAccess.Export
        public void set(String key, Object value, String syncMode) {
            set(key, value, syncMode, "hybrid");
        }

        @HostAccess.Export
        public void set(String key, Object value) {
            set(key, value, "batched", "hybrid");
        }

        @HostAccess.Export
        public Object get(String key) {
            return store.get(key);
        }

        @HostAccess.Export
        public boolean has(String key) {
            return store.has(key);
        }

        @HostAccess.Export
        public void remove(String key) {
            store.remove(key);
            manager.syncPlayerStore(player, store);
        }

        @HostAccess.Export
        public void on(String event, Value callback) {
            if ("change".equals(event)) {
                store.addChangeListener(callback);
            }
        }

        @HostAccess.Export
        public void onChange(String key, Value callback) {
            store.addKeyListener(key, callback);
        }

        private SharedValue.SyncMode parseSyncMode(String mode) {
            if (mode == null) return SharedValue.SyncMode.BATCHED;
            return "immediate".equalsIgnoreCase(mode) ? SharedValue.SyncMode.IMMEDIATE : SharedValue.SyncMode.BATCHED;
        }

        private SharedValue.Permission parsePermission(String permission) {
            if (permission == null) return SharedValue.Permission.HYBRID;
            return switch (permission.toLowerCase()) {
                case "server-only", "server_only" -> SharedValue.Permission.SERVER_ONLY;
                case "client-readonly", "client_readonly" -> SharedValue.Permission.CLIENT_READONLY;
                default -> SharedValue.Permission.HYBRID;
            };
        }
    }
}