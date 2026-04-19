package com.moud.client.fabric.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

public final class PlayerHeadLook {
    private static volatile boolean active;
    private static volatile double tx;
    private static volatile double ty;
    private static volatile double tz;

    private PlayerHeadLook() {
    }

    public static void set(double x, double y, double z) {
        tx = x; ty = y; tz = z;
        active = true;
    }

    public static void clear() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static float[] computeAbsoluteYawPitch() {
        if (!active) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc == null ? null : mc.player;
        if (p == null) return null;

        double eyeY = p.getY() + p.getStandingEyeHeight();
        double dx = tx - p.getX();
        double dy = ty - eyeY;
        double dz = tz - p.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(horiz, 1e-6)));
        return new float[]{yaw, MathHelper.clamp(pitch, -89f, 89f)};
    }
}
