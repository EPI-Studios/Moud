package com.moud.client.editor.scene;

import com.moud.network.MoudPackets;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class EditorSceneGraph {
    private final ConcurrentMap<String, SceneObject> objects = new ConcurrentHashMap<>();
    private volatile long version = 0;

    public void applySnapshot(MoudPackets.SceneStatePacket packet) {
        objects.clear();
        List<MoudPackets.SceneObjectSnapshot> snapshots = packet.objects();
        if (snapshots != null) {
            snapshots.forEach(snapshot -> objects.put(snapshot.objectId(), SceneObject.fromSnapshot(snapshot)));
        }
        version = packet.version();
    }

    public void applyAcknowledgement(MoudPackets.SceneEditAckPacket ack) {
        version = ack.serverVersion();
        if (!ack.success()) {
            return;
        }

        MoudPackets.SceneObjectSnapshot snapshot = ack.updatedObject();
        if (snapshot == null) {
            if (ack.objectId() != null) {
                objects.remove(ack.objectId());
            }
            return;
        }

        SceneObject object = SceneObject.fromSnapshot(snapshot);
        objects.put(object.getId(), object);
    }

    public Collection<SceneObject> getObjects() {
        return Collections.unmodifiableCollection(objects.values());
    }

    public long getVersion() {
        return version;
    }

    public SceneObject get(String objectId) {
        return objects.get(objectId);
    }

    public void remove(String objectId) {
        objects.remove(objectId);
    }

    public void mergeProperties(String objectId, Map<String, Object> properties) {
        SceneObject object = objects.get(objectId);
        if (object != null) {
            object.overwriteProperties(properties);
        }
    }
}
