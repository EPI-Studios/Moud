package com.moud.core.math;

import com.moud.core.util.MathUtils;

public final class YawConvention {
    private YawConvention() {}

    public static float mcFromMoud(float moudYawDeg) {
        return MathUtils.normalizeYaw(-moudYawDeg);
    }

    public static float moudFromMc(float mcYawDeg) {
        return MathUtils.normalizeYaw(-mcYawDeg);
    }

    public static double mcFromMoud(double moudYawDeg) {
        return MathUtils.normalizeYaw((float) -moudYawDeg);
    }

    public static double moudFromMc(double mcYawDeg) {
        return MathUtils.normalizeYaw((float) -mcYawDeg);
    }
}
