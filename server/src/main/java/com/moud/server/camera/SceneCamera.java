package com.moud.server.camera;

import com.moud.api.math.Vector3;

public record SceneCamera(
        String id,
        String label,
        Vector3 position,
        Vector3 rotation, // pitch, yaw, roll
        double fov,
        double nearPlane,
        double farPlane
) {
}
