package com.moud.server.editor.runtime;

import com.moud.api.math.Vector3;
import com.moud.server.camera.CameraRegistry;
import com.moud.server.camera.SceneCamera;
import com.moud.network.MoudPackets;

public final class CameraRuntimeAdapter implements SceneRuntimeAdapter {
    @Override
    public void create(MoudPackets.SceneObjectSnapshot snapshot) {
        CameraRegistry.getInstance().upsert(toCamera(snapshot));
    }

    @Override
    public void update(MoudPackets.SceneObjectSnapshot snapshot) {
        CameraRegistry.getInstance().upsert(toCamera(snapshot));
    }

    @Override
    public void remove() {

    }

    private SceneCamera toCamera(MoudPackets.SceneObjectSnapshot snapshot) {
        String id = snapshot.objectId();
        var props = snapshot.properties();
        Vector3 position = readVec3(props.get("position"), new Vector3(0, 70, 0));
        Vector3 rotation = readVec3(props.get("rotation"), new Vector3(0, 0, 0));
        double fov = readDouble(props.get("fov"), 70.0);
        double near = readDouble(props.get("near"), 0.1);
        double far = readDouble(props.get("far"), 128.0);
        String label = props.getOrDefault("label", id).toString();
        return new SceneCamera(id, label, position, rotation, fov, near, far);
    }

    private static double readDouble(Object raw, double fallback) {
        if (raw instanceof Number n) return n.doubleValue();
        try {
            return raw != null ? Double.parseDouble(raw.toString()) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Vector3 readVec3(Object raw, Vector3 fallback) {
        if (raw instanceof java.util.Map<?, ?> map) {
            boolean hasEuler = map.containsKey("pitch") || map.containsKey("yaw") || map.containsKey("roll");
            if (hasEuler) {
                double pitch = readDouble(map.get("pitch"), fallback.x);
                double yaw = readDouble(map.get("yaw"), fallback.y);
                double roll = readDouble(map.get("roll"), fallback.z);
                return new Vector3(pitch, yaw, roll);
            }
            double x = readDouble(map.get("x"), fallback.x);
            double y = readDouble(map.get("y"), fallback.y);
            double z = readDouble(map.get("z"), fallback.z);
            return new Vector3(x, y, z);
        }
        return fallback;
    }
}
