package com.meekdev.moud.script.api;

import com.meekdev.moud.core.math.Vec3;

public interface CameraRef {

    void shake(double trauma);

    void kick(double pitch, double yaw, double roll, double seconds);

    void fovPunch(double degrees, double seconds);

    void clearEffects();

    Vec3 worldToScreen(Vec3 world);

    Ray screenToRay(double x, double y);

    record Ray(Vec3 origin, Vec3 direction) {}
}
