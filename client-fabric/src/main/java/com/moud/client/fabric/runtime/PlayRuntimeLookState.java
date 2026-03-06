package com.moud.client.fabric.runtime;

import net.minecraft.client.MinecraftClient;

final class PlayRuntimeLookState {
    private static final float DEFAULT_LOOK_SENS_DEG_PER_PIXEL = 0.15f;

    private boolean mouseInit;
    private double lastMouseX;
    private double lastMouseY;
    private float yawDeg;
    private float pitchDeg;

    void resetMouse() {
        mouseInit = false;
    }

    void setAngles(float yawDeg, float pitchDeg) {
        this.yawDeg = PlayRuntimeAngles.normalizeYaw(yawDeg);
        this.pitchDeg = PlayRuntimeAngles.clampPitch(pitchDeg);
    }

    float yawDeg() {
        return yawDeg;
    }

    float pitchDeg() {
        return pitchDeg;
    }

    void onMouseMove(double x, double y) {
        if (!mouseInit) {
            mouseInit = true;
            lastMouseX = x;
            lastMouseY = y;
            return;
        }
        double dx = x - lastMouseX;
        double dy = y - lastMouseY;
        lastMouseX = x;
        lastMouseY = y;

        MinecraftClient client = MinecraftClient.getInstance();
        float sens = mouseDegPerPixel(client);
        yawDeg = PlayRuntimeAngles.normalizeYaw(yawDeg + (float) (dx * sens));
        pitchDeg = PlayRuntimeAngles.clampPitch(pitchDeg + (float) (dy * sens * invertYSign(client)));
    }

    void syncFromVanillaPlayer(MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }
        try {
            yawDeg = PlayRuntimeAngles.normalizeYaw(client.player.getYaw());
            pitchDeg = PlayRuntimeAngles.clampPitch(client.player.getPitch());
        } catch (Exception ignored) {
        }
    }

    private static float mouseDegPerPixel(MinecraftClient client) {
        if (client == null || client.options == null) {
            return DEFAULT_LOOK_SENS_DEG_PER_PIXEL;
        }
        try {
            Double value = (Double) client.options.getMouseSensitivity().getValue();
            double f = (value == null ? 0.5 : value) * 0.6 + 0.2;
            double scale = f * f * f * 8.0;
            return (float) (scale * 0.15);
        } catch (Exception ignored) {
            return DEFAULT_LOOK_SENS_DEG_PER_PIXEL;
        }
    }

    private static float invertYSign(MinecraftClient client) {
        if (client == null || client.options == null) {
            return 1.0f;
        }
        try {
            Boolean invert = (Boolean) client.options.getInvertYMouse().getValue();
            return invert != null && invert ? -1.0f : 1.0f;
        } catch (Exception ignored) {
            return 1.0f;
        }
    }
}

