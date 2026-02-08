package com.moud.client.ui.animation;

public enum EasingFunction {
    LINEAR(t -> t),
    EASE_IN(t -> t * t),
    EASE_OUT(t -> t * (2f - t)),
    EASE_IN_OUT(t -> t < 0.5f ? 2f * t * t : -1f + (4f - 2f * t) * t),
    EASE_IN_CUBIC(t -> t * t * t),
    EASE_OUT_CUBIC(t -> {
        float f = t - 1f;
        return f * f * f + 1f;
    }),
    EASE_IN_OUT_CUBIC(t -> t < 0.5f ? 4f * t * t * t : (t - 1f) * (2f * t - 2f) * (2f * t - 2f) + 1f);

    private final java.util.function.Function<Float, Float> function;

    EasingFunction(java.util.function.Function<Float, Float> function) {
        this.function = function;
    }

    public float apply(float t) {
        return function.apply(Math.max(0f, Math.min(1f, t)));
    }
}
