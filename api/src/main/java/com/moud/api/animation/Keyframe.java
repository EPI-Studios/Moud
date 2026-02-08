package com.moud.api.animation;

public record Keyframe(
        float time,
        float value,
        Interpolation interpolation,
        float inTangent,
        float outTangent
) {
    public enum Interpolation {
        STEP,
        LINEAR,
        SMOOTH,
        EASE_IN,
        EASE_OUT,
        BEZIER
    }
}
