package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.render.Camera;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class Cameras {

    private static final PropertyDef CFRAME = Classes.CAMERA.property("cframe");

    private static final double DEGREES = 180.0 / Math.PI;

    private Cameras() {}

    public static void frame(Camera camera, float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        switch (camera.mode) {
            case SCRIPTABLE -> hold(camera);
            case FIRST_PERSON -> first(camera, player, partialTick);
            case THIRD_PERSON -> third(camera, player, partialTick);
        }
        if (camera.fov > 0) {
            AmneticCamera.setFov((float) camera.fov);
        } else {
            AmneticCamera.clearFov();
        }
    }

    public static void release() {
        AmneticCamera.clearPose();
        AmneticCamera.clearFov();
    }

    private static void hold(Camera camera) {
        CFrame frame = camera.cframe;
        Vector3 look = frame.lookVector();
        pose(frame.position(), mcYaw(look), mcPitch(look), mcRoll(frame));
    }

    private static void first(Camera camera, LocalPlayer player, float partialTick) {
        AmneticCamera.clearPose();
        report(camera, eye(camera, player, partialTick), player.getYRot(), player.getXRot());
    }

    private static void third(Camera camera, LocalPlayer player, float partialTick) {
        Vector3 at = eye(camera, player, partialTick)
                .sub(moudLookFromMc(player.getYRot(), player.getXRot()).mul(camera.distance));
        pose(at, player.getYRot(), player.getXRot());
        report(camera, at, player.getYRot(), player.getXRot());
    }

    private static Vector3 eye(Camera camera, LocalPlayer player, float partialTick) {
        if (camera.subject != null && camera.subject.isAlive()) {
            return Transforms.world(camera.subject).position().add(camera.offset);
        }
        double x = player.xOld + (player.getX() - player.xOld) * partialTick;
        double y = player.yOld + (player.getY() - player.yOld) * partialTick;
        double z = player.zOld + (player.getZ() - player.zOld) * partialTick;
        return new Vector3(x, y, z).add(camera.offset);
    }

    private static Vector3 moudLookFromMc(float mcYaw, float mcPitch) {
        double yaw = mcYaw / DEGREES;
        double pitch = mcPitch / DEGREES;
        double cosPitch = Math.cos(pitch);
        return new Vector3(-Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch);
    }

    private static void pose(Vector3 at, float yaw, float pitch) {
        pose(at, yaw, pitch, 0f);
    }

    private static void pose(Vector3 at, float yaw, float pitch, float roll) {
        AmneticCamera.setPose(
                new Vec3(at.x(), at.y(), at.z()), yaw, pitch, roll);
    }

    private static void report(Camera camera, Vector3 at, float yaw, float pitch) {
        Instances.setObj(camera, CFRAME,
                new CFrame(at, Quat.lookAt(moudLookFromMc(yaw, pitch), Vector3.UP)));
    }

    private static float mcYaw(Vector3 look) {
        return (float) (Math.atan2(-look.x(), look.z()) * DEGREES);
    }

    private static float mcRoll(CFrame frame) {
        return (float) (-frame.roll(Vector3.UP) * DEGREES);
    }

    private static float mcPitch(Vector3 look) {
        return (float) (Math.asin(Math.max(-1.0, Math.min(1.0, -look.y()))) * DEGREES);
    }
}
