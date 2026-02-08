package com.moud.client.editor.scene;

import com.moud.network.MoudPackets;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SceneObject {
    private final String id;
    private final String type;
    private final ConcurrentHashMap<String, Object> properties;

    private SceneObject(String id, String type, Map<String, Object> properties) {
        this.id = id;
        this.type = type;
        this.properties = new ConcurrentHashMap<>(properties);
    }

    public static SceneObject fromSnapshot(MoudPackets.SceneObjectSnapshot snapshot) {
        return new SceneObject(snapshot.objectId(), snapshot.objectType(), snapshot.properties());
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    public MoudPackets.SceneObjectSnapshot toSnapshot() {
        return new MoudPackets.SceneObjectSnapshot(id, type, new ConcurrentHashMap<>(properties));
    }

    public void overwriteProperties(Map<String, Object> newProperties) {
        properties.clear();
        properties.putAll(newProperties);
    }

    public void putProperty(String key, Object value) {
        properties.put(key, value);
    }

}
