package com.moud.client.fabric.runtime;

import com.moud.core.util.MathUtils;

final class PlayRuntimeAngles {
    static final float MIN_PITCH = -89.0f;
    static final float MAX_PITCH = 89.0f;

    private PlayRuntimeAngles() {
    }

    static float clampPitch(float pitchDeg) {
        return MathUtils.clampPitch(pitchDeg, MIN_PITCH, MAX_PITCH);
    }

    static float normalizeYaw(float yawDeg) {
        return MathUtils.normalizeYaw(yawDeg);
    }
}
