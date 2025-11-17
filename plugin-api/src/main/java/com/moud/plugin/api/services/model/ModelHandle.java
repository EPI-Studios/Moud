package com.moud.plugin.api.services.model;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;

public interface ModelHandle extends AutoCloseable {
    long id();
    Vector3 position();
    Quaternion rotation();
    Vector3 scale();
    void setPosition(Vector3 position);
    void setRotation(Quaternion rotation);
    void setScale(Vector3 scale);
    void setTexture(String texturePath);
    void remove();

    @Override
    default void close() {
        remove();
    }
}
