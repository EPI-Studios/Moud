package com.moud.plugin.api.services.rendering;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;

public record ClientPerspectiveCameraOptions(Vector3 position, Quaternion rotation, Vector3 lookAt, Vector3 up) {

    public ClientPerspectiveCameraOptions(Vector3 position, Quaternion rotation) {
        this(position, rotation, null, null);
    }

    public static ClientPerspectiveCameraOptions of(Vector3 position, Quaternion rotation) {
        return new ClientPerspectiveCameraOptions(position, rotation, null, null);
    }

    public static ClientPerspectiveCameraOptions ofLookAt(Vector3 position, Vector3 lookAt) {
        return new ClientPerspectiveCameraOptions(position, null, lookAt, null);
    }

    public static ClientPerspectiveCameraOptions ofLookAt(Vector3 position, Vector3 lookAt, Vector3 up) {
        return new ClientPerspectiveCameraOptions(position, null, lookAt, up);
    }
}
