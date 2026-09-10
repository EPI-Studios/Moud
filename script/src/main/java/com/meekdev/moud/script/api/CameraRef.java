package com.meekdev.moud.script.api;

import com.meekdev.moud.core.math.Vec3;

// the camera verbs that need the renderer to answer them
//
// the pose is a property and lives on the instance; these are the things a frame of state cannot
// express -- a shake that decays, a projection only the renderer knows
public interface CameraRef {

    void shake(double trauma);

    void kick(double pitch, double yaw, double roll, double seconds);

    void fovPunch(double degrees, double seconds);

    void clearEffects();

    // null when the point is behind the camera, which is a real answer and not a failure
    Vec3 worldToScreen(Vec3 world);

    Ray screenToRay(double x, double y);

    record Ray(Vec3 origin, Vec3 direction) {}
}
