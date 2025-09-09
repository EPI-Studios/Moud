package com.moud.client.shared.core;

import org.graalvm.polyglot.Value;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientValueCache {
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
        Object oldValue = values.get(key);
        values.put(key, value);
        modifiableKeys.put(key, true);
        triggerChangeListeners(key, value, oldValue);
    }

    public void updateOptimistic(String key, Object value) {
        Object oldValue = values.get(key);
        values.put(key, value);
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
        for (Value listener : changeListeners) {
            try {
                listener.execute(key, newValue, oldValue);
            } catch (Exception e) {
                changeListeners.remove(listener);
            }
        }

        CopyOnWriteArrayList<Value> listeners = keyListeners.get(key);
        if (listeners != null) {
            for (Value listener : listeners) {
                try {
                    listener.execute(newValue, oldValue);
                } catch (Exception e) {
                    listeners.remove(listener);
                }
            }
        }
    }

    private Object cloneValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(value);
            return mapper.readValue(json, Object.class);
        } catch (Exception e) {
            return value;
        }
    }

    public void clear() {
        values.clear();
        modifiableKeys.clear();
        changeListeners.clear();
        keyListeners.clear();
    }
}