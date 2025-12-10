package com.moud.api.animation;

import java.util.List;

public record PropertyTrack(
        String propertyPath,
        PropertyType propertyType,
        float minValue,
        float maxValue,
        List<Keyframe> keyframes
) {
    public enum PropertyType {
        FLOAT,
        ANGLE,
        COLOR,
        QUATERNION
    }
}
