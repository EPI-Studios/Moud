package com.moud.client.shared.core;

import com.moud.client.runtime.ClientScriptingRuntime;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientValueCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientValueCache.class);
    private final String storeName;
    private final ConcurrentHashMap<String, Object> values;
    private final ConcurrentHashMap<String, Boolean> modifiableKeys;
    private final CopyOnWriteArrayList<Value> changeListeners;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Value>> keyListeners;

    public ClientValueCache(String storeName) {
        this.storeName = storeName;
        this.values = new ConcurrentHashMap<>();
        this.modifiableKeys = new ConcurrentHashMap<>();
        this.changeListeners = new CopyOnWriteArrayList<>();
        this.keyListeners = new ConcurrentHashMap<>();
    }

    public Object get(String key) {
        return cloneValue(values.get(key));
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public boolean canModify(String key) {
        return modifiableKeys.getOrDefault(key, false);
    }

    public void updateFromServer(String key, Object value) {
        Object oldValue = values.put(key, value);
        modifiableKeys.put(key, true);
        triggerChangeListeners(key, value, oldValue);
    }

    public void updateOptimistic(String key, Object value) {
        Object oldValue = values.put(key, value);
        triggerChangeListeners(key, value, oldValue);
    }

    public void addChangeListener(Value callback) {
        if (callback != null && callback.canExecute()) {
            changeListeners.add(callback);
        }
    }

    public void addKeyListener(String key, Value callback) {
        if (callback != null && callback.canExecute()) {
            keyListeners.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(callback);
        }
    }

    public void removeChangeListener(Value callback) {
        changeListeners.remove(callback);
    }

    public String getStoreName() {
        return storeName;
    }

    private void triggerChangeListeners(String key, Object newValue, Object oldValue) {
        // THIS IS THE FIX:
        // We schedule the JavaScript callbacks to run on the main script thread
        // instead of executing them directly on the Netty network thread.

        for (Value listener : changeListeners) {
            ClientScriptingRuntime.scheduleScriptTask(() -> {
                try {
                    if (listener.canExecute()) {
                        // Pass cloned values to prevent any potential cross-thread modification issues
                        listener.execute(key, cloneValue(newValue), cloneValue(oldValue));
                    }
                } catch (Exception e) {
                    LOGGER.error("Error in shared value 'change' listener for store '{}'", storeName, e);
                    changeListeners.remove(listener); // Remove faulty listener
                }
            });
        }

        CopyOnWriteArrayList<Value> specificKeyListeners = keyListeners.get(key);
        if (specificKeyListeners != null) {
            for (Value listener : specificKeyListeners) {
                ClientScriptingRuntime.scheduleScriptTask(() -> {
                    try {
                        if (listener.canExecute()) {
                            listener.execute(cloneValue(newValue), cloneValue(oldValue));
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error in shared value key '{}' listener for store '{}'", key, storeName, e);
                        specificKeyListeners.remove(listener); // Remove faulty listener
                    }
                });
            }
        }
    }

    private Object cloneValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        try {
            // Use Jackson for a deep clone to be safe
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(value);
            return mapper.readValue(json, Object.class);
        } catch (Exception e) {
            LOGGER.warn("Could not deep clone value of type {}, returning original. This may cause issues.", value.getClass().getName());
            return value; // Fallback to shallow copy
        }
    }

    public void clear() {
        values.clear();
        modifiableKeys.clear();
        changeListeners.clear();
        keyListeners.clear();
    }
}