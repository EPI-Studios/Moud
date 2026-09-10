package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.meekdev.moud.core.instance.Camera;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

// the camera instance in, amnetic's pose out
//
// only scriptable holds an absolute pose. the follow modes let vanilla aim, because the mouse
// already turns the player and taking that over means reimplementing sensitivity, smoothing and
// every accessibility option attached to it for no gain
public final class Cameras {

    private static final PropertyDef CFRAME = Classes.CAMERA.property("cframe");

    private static final double DEGREES = Math.PI / 180.0;

    private Cameras() {}

    public static void frame(Camera camera, float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        AmneticCamera.setFov((float) camera.fov);
        switch (camera.mode) {
            case SCRIPTABLE -> hold(camera);
            case FIRST_PERSON -> follow(camera, player, partialTick, 0);
            case THIRD_PERSON -> follow(camera, player, partialTick, camera.distance);
        }
    }

    public static void release() {
        AmneticCamera.clearPose();
        AmneticCamera.clearFov();
    }

    // the place wrote the pose, so it is the pose. yaw and pitch come back out of the frame
    // because amnetic takes them as angles, not as a basis
    private static void hold(Camera camera) {
        CFrame frame = camera.cframe;
        Vec3 position = frame.position();
        Quat r = frame.rotation();
        double yaw = Math.atan2(2.0 * (r.w() * r.y() + r.x() * r.z()),
                1.0 - 2.0 * (r.y() * r.y() + r.x() * r.x()));
        double pitch = Math.asin(Math.max(-1.0, Math.min(1.0,
                2.0 * (r.w() * r.x() - r.y() * r.z()))));
        // minecraft's Vec3 against ours, the one clash 20.1 keeps a qualified name for
        AmneticCamera.setPose(
                new net.minecraft.world.phys.Vec3(position.x(), position.y(), position.z()),
                (float) (-yaw / DEGREES), (float) (-pitch / DEGREES));
    }

    // vanilla draws it, and the instance is told where it ended up so a place can read the camera
    // it is actually looking through rather than the one it asked for
    private static void follow(Camera camera, LocalPlayer player, float partialTick, double back) {
        AmneticCamera.clearPose();

        double yaw = player.getYRot() * DEGREES;
        double pitch = player.getXRot() * DEGREES;
        double x = player.xOld + (player.getX() - player.xOld) * partialTick;
        double y = player.yOld + (player.getY() - player.yOld) * partialTick;
        double z = player.zOld + (player.getZ() - player.zOld) * partialTick;

        Vec3 eye = new Vec3(x, y, z).add(camera.offset);
        if (back > 0) {
            double cosPitch = Math.cos(pitch);
            eye = eye.sub(new Vec3(-Math.sin(yaw) * cosPitch, -Math.sin(pitch),
                    Math.cos(yaw) * cosPitch).mul(back));
        }
        Instances.setObj(camera, CFRAME,
                new CFrame(eye, Quat.euler(-pitch, -yaw, 0)));
    }
}
