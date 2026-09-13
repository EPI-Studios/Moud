package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.meekdev.amnetic.client.camera.CameraEffects;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.script.api.CameraRef;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

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

    @Override
    public Vector3 worldToScreen(Vector3 world) {
        if (!AmneticCamera.isReady()) return null;
        Vec3 point =
                new Vec3(world.x(), world.y(), world.z());
        Vector2f screen = AmneticCamera.worldToScreen(point);
        if (screen == null) return null;
        return new Vector3(screen.x(), screen.y(), AmneticCamera.distanceTo(point));
    }

    @Override
    public CameraRef.Ray screenToRay(double x, double y) {
        var ray = AmneticCamera.screenToRay(x, y);
        return new CameraRef.Ray(toVector3(ray.origin()), toVector3(ray.direction()));
    }

    private static Vector3 toVector3(Vec3 v) {
        return new Vector3(v.x(), v.y(), v.z());
    }
}
