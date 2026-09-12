package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.meekdev.amnetic.client.camera.CameraEffects;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.script.api.CameraRef;
import org.joml.Vector2f;

// the camera verbs, straight onto amnetic. nothing is cached here: it already owns all of it
public final class CameraApi implements CameraRef {

    @Override
    public void shake(double trauma) {
        CameraEffects.shake((float) trauma);
    }

    @Override
    public void kick(double pitch, double yaw, double roll, double seconds) {
        CameraEffects.kick((float) pitch, (float) yaw, (float) roll, (float) seconds);
    }

    @Override
    public void fovPunch(double degrees, double seconds) {
        CameraEffects.fovPunch((float) degrees, (float) seconds);
    }

    @Override
    public void clearEffects() {
        CameraEffects.clear();
    }

    // a point behind the camera has no place on screen, and amnetic already says so by handing
    // back nothing rather than a plausible pixel. asking it twice was the bug: the ndc check
    // divides by a w that is negative behind the camera, so it agreed when it should not have
    @Override
    public Vec3 worldToScreen(Vec3 world) {
        if (!AmneticCamera.isReady()) return null;
        // minecraft's Vec3 against ours, the one clash 20.1 keeps a qualified name for
        net.minecraft.world.phys.Vec3 point =
                new net.minecraft.world.phys.Vec3(world.x(), world.y(), world.z());
        Vector2f screen = AmneticCamera.worldToScreen(point);
        if (screen == null) return null;
        return new Vec3(screen.x(), screen.y(), AmneticCamera.distanceTo(point));
    }

    @Override
    public CameraRef.Ray screenToRay(double x, double y) {
        // spelled out because Ray is taken: this class implements CameraRef, and an inherited
        // nested type shadows a single type import
        com.meekdev.amnetic.client.camera.Ray ray = AmneticCamera.screenToRay(x, y);
        return new CameraRef.Ray(ours(ray.origin()), ours(ray.direction()));
    }

    private static Vec3 ours(net.minecraft.world.phys.Vec3 v) {
        return new Vec3(v.x(), v.y(), v.z());
    }
}
