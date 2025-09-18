package com.moud.client.player;

import org.joml.Vector3f;

public class PartAnimation {
    private final Vector3f rotation;
    private final Vector3f translation;
    private final Boolean visible;

    public PartAnimation(Vector3f rotation, Vector3f translation, Boolean visible) {
        this.rotation = rotation;
        this.translation = translation;
        this.visible = visible;
    }

    public Vector3f getRotation() {
        return rotation;
    }

    public Vector3f getTranslation() {
        return translation;
    }

    public Boolean isVisible() {
        return visible;
    }
}