package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Camera;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

// the camera instance in, amnetic's pose out
//
// aiming stays the player's in the follow modes: the mouse already turns them, and taking that
// over means reimplementing sensitivity, smoothing and every accessibility option attached to it.
// where the camera *is* is ours, and that is the whole of third person
public final class Cameras {

    private static final PropertyDef CFRAME = Classes.CAMERA.property("cframe");

    private static final double DEGREES = 180.0 / Math.PI;

    private Cameras() {}

    public static void frame(Camera camera, float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        AmneticCamera.setFov((float) camera.fov);
        switch (camera.mode) {
            case SCRIPTABLE -> hold(camera);
            case FIRST_PERSON -> first(camera, player, partialTick);
            case THIRD_PERSON -> third(camera, player, partialTick);
        }
    }

    public static void release() {
        AmneticCamera.clearPose();
        AmneticCamera.clearFov();
    }

    // the place wrote the pose, so it is the pose, roll included. a frame carries roll in its
    // basis and dropping it means a place can write a tilt that never appears
    private static void hold(Camera camera) {
        CFrame frame = camera.cframe;
        Vec3 look = frame.lookVector();
        pose(frame.position(), mcYaw(look), mcPitch(look), mcRoll(frame));
    }

    private static void first(Camera camera, LocalPlayer player, float partialTick) {
        AmneticCamera.clearPose();
        report(camera, eye(camera, player, partialTick), player.getYRot(), player.getXRot());
    }

    // pulled back along the look, which needs an absolute pose. clearing it instead leaves vanilla
    // drawing from the player's own eye, and the mode then does nothing at all
    private static void third(Camera camera, LocalPlayer player, float partialTick) {
        Vec3 at = eye(camera, player, partialTick)
                .sub(moudLookFromMc(player.getYRot(), player.getXRot()).mul(camera.distance));
        pose(at, player.getYRot(), player.getXRot());
        report(camera, at, player.getYRot(), player.getXRot());
    }

    // a subject is followed where it is, not where it was a tick ago: it is an instance, and
    // the tree is already the interpolated view of one. the player is the entity, which is not
    private static Vec3 eye(Camera camera, LocalPlayer player, float partialTick) {
        if (camera.subject != null && camera.subject.isAlive()) {
            return Transforms.world(camera.subject).position().add(camera.offset);
        }
        double x = player.xOld + (player.getX() - player.xOld) * partialTick;
        double y = player.yOld + (player.getY() - player.yOld) * partialTick;
        double z = player.zOld + (player.getZ() - player.zOld) * partialTick;
        return new Vec3(x, y, z).add(camera.offset);
    }

    // 6.2.5: the conversion happens here and in mcYaw/mcPitch, and nowhere else. our forward is
    // -z and minecraft's yaw zero looks down +z, so the two differ by half a turn rather than by
    // a sign -- which is exactly the inline negate that section says to reject
    private static Vec3 moudLookFromMc(float mcYaw, float mcPitch) {
        double yaw = mcYaw / DEGREES;
        double pitch = mcPitch / DEGREES;
        double cosPitch = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch);
    }

    private static void pose(Vec3 at, float yaw, float pitch) {
        pose(at, yaw, pitch, 0f);
    }

    private static void pose(Vec3 at, float yaw, float pitch, float roll) {
        // minecraft's Vec3 against ours, the one clash 20.1 keeps a qualified name for
        AmneticCamera.setPose(
                new net.minecraft.world.phys.Vec3(at.x(), at.y(), at.z()), yaw, pitch, roll);
    }

    // the instance is told where the camera ended up, so a place reads the one it is looking
    // through rather than the one it asked for
    //
    // built from the look vector rather than from euler angles, because a frame that round trips
    // through mcYaw is worth more than one that happens to agree with a rotation order
    private static void report(Camera camera, Vec3 at, float yaw, float pitch) {
        Instances.setObj(camera, CFRAME,
                new CFrame(at, Quat.lookAt(moudLookFromMc(yaw, pitch), Vec3.UP)));
    }

    // minecraft states a look as yaw clockwise from south and pitch downward, which is neither our
    // convention nor a quaternion. this is the only place either is converted
    private static float mcYaw(Vec3 look) {
        return (float) (Math.atan2(-look.x(), look.z()) * DEGREES);
    }

    // minecraft states every angle the opposite way round from a right handed rotation, which
    // yaw and pitch below already show. roll follows them rather than being special
    private static float mcRoll(CFrame frame) {
        return (float) (-frame.roll(Vec3.UP) * DEGREES);
    }

    private static float mcPitch(Vec3 look) {
        return (float) (Math.asin(Math.max(-1.0, Math.min(1.0, -look.y()))) * DEGREES);
    }
}
