package com.moud.server.shared.core;

import org.graalvm.polyglot.Value;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;
import java.util.List;
import java.util.HashMap;

public class SharedValueStore {
    private final String storeName;
    private final String playerId;
    private final ConcurrentHashMap<String, SharedValue> values;
    private final CopyOnWriteArrayList<Value> changeListeners;
    private final CopyOnWriteArrayList<Value> keyListeners;

    public SharedValueStore(String storeName, String playerId) {
        this.storeName = storeName;
        this.playerId = playerId;
        this.values = new ConcurrentHashMap<>();
        this.changeListeners = new CopyOnWriteArrayList<>();
        this.keyListeners = new CopyOnWriteArrayList<>();
    }

    public void set(String key, Object value, SharedValue.Permission permission, SharedValue.SyncMode syncMode) {
        SharedValue oldSharedValue = values.get(key);
        Object oldValue = oldSharedValue != null ? oldSharedValue.getValue() : null;

        SharedValue newSharedValue = new SharedValue(key, value, permission, syncMode);
        values.put(key, newSharedValue);

        triggerChangeListeners(key, value, oldValue);
    }

    public Object get(String key) {
        SharedValue sharedValue = values.get(key);
        return sharedValue != null ? sharedValue.getClonedValue() : null;
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public void remove(String key) {
        SharedValue removed = values.remove(key);
        if (removed != null) {
            triggerChangeListeners(key, null, removed.getValue());
        }
    }

    public boolean canClientModify(String key) {
        SharedValue sharedValue = values.get(key);
        return sharedValue != null && sharedValue.canClientModify();
    }

    public boolean updateFromClient(String key, Object value) {
        SharedValue sharedValue = values.get(key);
        if (sharedValue == null || !sharedValue.canClientModify()) {
            return false;
        }

        Object oldValue = sharedValue.getValue();
        sharedValue.setValue(value);
        triggerChangeListeners(key, value, oldValue);
        return true;
    }

    public Map<String, Object> getDirtyValues() {
        Map<String, Object> dirty = new HashMap<>();
        for (SharedValue value : values.values()) {
            if (value.isDirty() && value.shouldSync()) {
                dirty.put(value.getKey(), value.getClonedValue());
            }
        }
        return dirty;
    }

    public Map<String, Object> getImmediateValues() {
        Map<String, Object> immediate = new HashMap<>();
        for (SharedValue value : values.values()) {
            if (value.isDirty() && value.shouldSync() && value.getSyncMode() == SharedValue.SyncMode.IMMEDIATE) {
                immediate.put(value.getKey(), value.getClonedValue());
            }
        }
        return immediate;
    }

    public void markAllClean() {
        values.values().forEach(SharedValue::markClean);
    }

    public void markClean(String key) {
        SharedValue value = values.get(key);
        if (value != null) {
            value.markClean();
        }
    }

    public Map<String, Object> getAllValues() {
        Map<String, Object> all = new HashMap<>();
        for (SharedValue value : values.values()) {
            if (value.shouldSync()) {
                all.put(value.getKey(), value.getClonedValue());
            }
        }
        return all;
    }

    public void addChangeListener(Value callback) {
        if (callback != null && callback.canExecute()) {
            changeListeners.add(callback);
        }
    }

    public void addKeyListener(String key, Value callback) {
        if (callback != null && callback.canExecute()) {
            keyListeners.add(callback);
        }
    }

    public void removeChangeListener(Value callback) {
        changeListeners.remove(callback);
    }

    public String getStoreName() {
        return storeName;
    }

    public String getPlayerId() {
        return playerId;
    }

    private void triggerChangeListeners(String key, Object newValue, Object oldValue) {
        for (Value listener : changeListeners) {
            try {
                listener.execute(key, newValue, oldValue);
            } catch (Exception e) {
                changeListeners.remove(listener);
            }
        }

        for (Value listener : keyListeners) {
            try {
                listener.execute(key, newValue, oldValue);
            } catch (Exception e) {
                keyListeners.remove(listener);
            }
        }
    }

    public void clear() {
        values.clear();
        changeListeners.clear();
        keyListeners.clear();
    }
}