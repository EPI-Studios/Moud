package com.moud.server.shared.api;

import com.moud.server.shared.SharedValueManager;
import com.moud.server.shared.core.SharedValue;
import com.moud.server.shared.core.SharedValueStore;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.Value;

public class SharedValueApiProxy {
    private final Player player;
    private final SharedValueManager manager;

    public SharedValueApiProxy(Player player) {
        this.player = player;
        this.manager = SharedValueManager.getInstance();
    }

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

        public void set(String key, Object value) {
            set(key, value, "batched", "hybrid");
        }

        public void set(String key, Object value, String syncMode) {
            set(key, value, syncMode, "hybrid");
        }

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

        public Object get(String key) {
            return store.get(key);
        }

        public boolean has(String key) {
            return store.has(key);
        }

        public void remove(String key) {
            store.remove(key);
            manager.syncPlayerStore(player, store);
        }

        public void on(String event, Value callback) {
            if ("change".equals(event)) {
                store.addChangeListener(callback);
            }
        }

        public void onChange(String key, Value callback) {
            store.addKeyListener(key, callback);
        }

        private SharedValue.SyncMode parseSyncMode(String mode) {
            return switch (mode.toLowerCase()) {
                case "immediate" -> SharedValue.SyncMode.IMMEDIATE;
                case "batched" -> SharedValue.SyncMode.BATCHED;
                default -> SharedValue.SyncMode.BATCHED;
            };
        }

        private SharedValue.Permission parsePermission(String permission) {
            return switch (permission.toLowerCase()) {
                case "server-only", "server_only" -> SharedValue.Permission.SERVER_ONLY;
                case "hybrid" -> SharedValue.Permission.HYBRID;
                case "client-readonly", "client_readonly" -> SharedValue.Permission.CLIENT_READONLY;
                default -> SharedValue.Permission.HYBRID;
            };
        }
    }
}