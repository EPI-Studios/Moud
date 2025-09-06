package com.moud.api.camera;

import com.moud.api.math.Vector3;
import com.moud.api.math.Quaternion;

public interface CameraController {

    Vector3 getPosition();
    void setPosition(Vector3 position);

    Quaternion getRotation();
    void setRotation(Quaternion rotation);

    float getPitch();
    void setPitch(float pitch);

    float getYaw();
    void setYaw(float yaw);

    float getRoll();
    void setRoll(float roll);

    Vector3 getForward();
    Vector3 getRight();
    Vector3 getUp();

    void lookAt(Vector3 target);

    void enableSmoothing(boolean enabled);
    boolean isSmoothingEnabled();

    void setSmoothingFactor(float factor);
    float getSmoothingFactor();

    void updateMouseInput(float deltaX, float deltaY, float deltaTime);

    void setMouseSensitivity(float sensitivity);
    float getMouseSensitivity();

    void setInvertY(boolean invert);
    boolean isYInverted();

    void update(float deltaTime);
}