package com.meekdev.moud.script.api;

import com.meekdev.moud.core.math.Vector3;

public interface CameraRef {

    void shake(double trauma);

    void kick(double pitch, double yaw, double roll, double seconds);

    void fovPunch(double degrees, double seconds);

    void clearEffects();

    Vector3 worldToScreen(Vector3 world);

    Ray screenToRay(double x, double y);

    record Ray(Vector3 origin, Vector3 direction) {}
}
