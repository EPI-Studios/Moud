package com.moud.client.fabric.runtime;

final class PlayRuntimeAngles {
    static final float MIN_PITCH = -89.0f;
    static final float MAX_PITCH = 89.0f;

    private PlayRuntimeAngles() {
    }

    static float clampPitch(float pitchDeg) {
        if (!Float.isFinite(pitchDeg)) {
            return 0.0f;
        }
        return Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitchDeg));
    }

    static float normalizeYaw(float yawDeg) {
        if (!Float.isFinite(yawDeg)) {
            return 0.0f;
        }
        float wrapped = (float) (yawDeg % 360.0);
        if (wrapped > 180.0f) {
            wrapped -= 360.0f;
        } else if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }
}

