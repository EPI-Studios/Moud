package com.moud.client.fabric.runtime;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

public final class CameraLookTarget {
    private static volatile boolean active;
    private static volatile boolean hard;
    private static volatile double tx;
    private static volatile double ty;
    private static volatile double tz;
    private static volatile float strength;
    private static volatile float maxAngleDeg;

    private CameraLookTarget() {
    }

    public static void setSoft(double x, double y, double z, float strength, float maxAngleDeg) {
        tx = x; ty = y; tz = z;
        CameraLookTarget.strength = Math.max(0f, strength);
        CameraLookTarget.maxAngleDeg = maxAngleDeg;
        hard = false;
        active = true;
    }

    public static void setHard(double x, double y, double z) {
        tx = x; ty = y; tz = z;
        hard = true;
        active = true;
    }

    public static void clear() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static void applyToLocalPlayer(float dt) {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc == null ? null : mc.player;
        if (p == null) return;

        double eyeY = p.getY() + p.getStandingEyeHeight();
        double dx = tx - p.getX();
        double dy = ty - eyeY;
        double dz = tz - p.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(horiz, 1e-6)));

        if (hard) {
            p.setYaw(targetYaw);
            p.setPitch(MathHelper.clamp(targetPitch, -89f, 89f));
            return;
        }

        float yaw = p.getYaw();
        float pitch = p.getPitch();
        float deltaYaw = MathHelper.wrapDegrees(targetYaw - yaw);
        float deltaPitch = targetPitch - pitch;
        float t = 1f - (float) Math.exp(-strength * Math.max(dt, 0f));
        float newYaw = yaw + deltaYaw * t;
        float newPitch = pitch + deltaPitch * t;

        if (maxAngleDeg > 0f) {
            float remainYaw = MathHelper.wrapDegrees(newYaw - targetYaw);
            float remainPitch = newPitch - targetPitch;
            float ang = (float) Math.sqrt(remainYaw * remainYaw + remainPitch * remainPitch);
            if (ang > maxAngleDeg) {
                float s = maxAngleDeg / ang;
                newYaw = targetYaw + remainYaw * s;
                newPitch = targetPitch + remainPitch * s;
            }
        }

        p.setYaw(newYaw);
        p.setPitch(MathHelper.clamp(newPitch, -89f, 89f));
    }
}
