package com.moud.server.camera;

import com.moud.api.math.Vector3;
import org.graalvm.polyglot.HostAccess;

public record SceneCamera(
        String id,
        String label,
        Vector3 position,
        Vector3 rotation, // pitch, yaw, roll
        double fov,
        double nearPlane,
        double farPlane
) {
    @HostAccess.Export
    public String getId() {
        return this.id;
    }

    @HostAccess.Export
    public String getLabel() {
        return this.label;
    }

    @HostAccess.Export
    public Vector3 getPosition() {
        return this.position;
    }

    @HostAccess.Export
    public Vector3 getRotation() {
        return this.rotation;
    }

    @HostAccess.Export
    public double getFov() {
        return this.fov;
    }

    @HostAccess.Export
    public double getNearPlane() {
        return this.nearPlane;
    }

    @HostAccess.Export
    public double getFarPlane() {
        return this.farPlane;
    }
}
