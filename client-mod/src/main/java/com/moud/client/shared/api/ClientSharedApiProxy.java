package com.moud.client.shared.api;

import com.moud.client.shared.SharedValueManager;
import com.moud.client.shared.core.ClientValueCache;
import org.graalvm.polyglot.Value;

public class ClientSharedApiProxy {
    private final SharedValueManager manager;

    public ClientSharedApiProxy() {
        this.manager = SharedValueManager.getInstance();
    }

    public ClientStoreProxy getStore(String storeName) {
        ClientValueCache store = manager.getOrCreateStore(storeName);
        return new ClientStoreProxy(store, manager);
    }

    public static class ClientStoreProxy {
        private final ClientValueCache store;
        private final SharedValueManager manager;

        public ClientStoreProxy(ClientValueCache store, SharedValueManager manager) {
            this.store = store;
            this.manager = manager;
        }

        public Object get(String key) {
            return store.get(key);
        }

        public boolean has(String key) {
            return store.has(key);
        }

        public boolean set(String key, Object value) {
            return manager.requestUpdate(store.getStoreName(), key, value);
        }

        public void on(String event, Value callback) {
            if ("change".equals(event)) {
                store.addChangeListener(callback);
            }
        }

        public void onChange(String key, Value callback) {
            store.addKeyListener(key, callback);
        }

        public boolean canModify(String key) {
            return store.canModify(key);
        }
    }
}