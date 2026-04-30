package com.moud.core.tween;

public final class Easing {

    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float HALF_PI = (float) (Math.PI * 0.5);
    private static final float BACK_C1 = 1.70158f;
    private static final float BACK_C2 = BACK_C1 * 1.525f;
    private static final float BACK_C3 = BACK_C1 + 1.0f;
    private static final float ELASTIC_C4 = TAU / 3.0f;
    private static final float ELASTIC_C5 = TAU / 4.5f;
    private static final float BOUNCE_N = 7.5625f;
    private static final float BOUNCE_D = 2.75f;

    private Easing() {
    }

    public static float apply(EasingMode mode, float t) {
        if (t <= 0.0f) {
            return 0.0f;
        }
        if (t >= 1.0f) {
            return 1.0f;
        }
        return switch (mode) {
            case LINEAR -> t;
            case SINE_IN -> 1.0f - (float) Math.cos(t * HALF_PI);
            case SINE_OUT -> (float) Math.sin(t * HALF_PI);
            case SINE_IN_OUT -> -0.5f * ((float) Math.cos(Math.PI * t) - 1.0f);
            case QUAD_IN -> t * t;
            case QUAD_OUT -> 1.0f - (1.0f - t) * (1.0f - t);
            case QUAD_IN_OUT -> t < 0.5f ? 2.0f * t * t : 1.0f - sq(-2.0f * t + 2.0f) * 0.5f;
            case CUBIC_IN -> t * t * t;
            case CUBIC_OUT -> 1.0f - cube(1.0f - t);
            case CUBIC_IN_OUT -> t < 0.5f ? 4.0f * t * t * t : 1.0f - cube(-2.0f * t + 2.0f) * 0.5f;
            case EXPO_IN -> (float) Math.pow(2.0, 10.0 * (t - 1.0));
            case EXPO_OUT -> 1.0f - (float) Math.pow(2.0, -10.0 * t);
            case EXPO_IN_OUT -> t < 0.5f
                    ? (float) Math.pow(2.0, 20.0 * t - 10.0) * 0.5f
                    : (2.0f - (float) Math.pow(2.0, -20.0 * t + 10.0)) * 0.5f;
            case BACK_IN -> BACK_C3 * t * t * t - BACK_C1 * t * t;
            case BACK_OUT -> 1.0f + BACK_C3 * cube(t - 1.0f) + BACK_C1 * sq(t - 1.0f);
            case BACK_IN_OUT -> t < 0.5f
                    ? sq(2.0f * t) * ((BACK_C2 + 1.0f) * 2.0f * t - BACK_C2) * 0.5f
                    : (sq(2.0f * t - 2.0f) * ((BACK_C2 + 1.0f) * (2.0f * t - 2.0f) + BACK_C2) + 2.0f) * 0.5f;
            case ELASTIC_IN -> -((float) Math.pow(2.0, 10.0 * t - 10.0))
                    * (float) Math.sin((t * 10.0f - 10.75f) * ELASTIC_C4);
            case ELASTIC_OUT -> ((float) Math.pow(2.0, -10.0 * t))
                    * (float) Math.sin((t * 10.0f - 0.75f) * ELASTIC_C4) + 1.0f;
            case ELASTIC_IN_OUT -> t < 0.5f
                    ? -((float) Math.pow(2.0, 20.0 * t - 10.0)
                        * (float) Math.sin((20.0f * t - 11.125f) * ELASTIC_C5)) * 0.5f
                    : ((float) Math.pow(2.0, -20.0 * t + 10.0)
                        * (float) Math.sin((20.0f * t - 11.125f) * ELASTIC_C5)) * 0.5f + 1.0f;
            case BOUNCE_IN -> 1.0f - bounceOut(1.0f - t);
            case BOUNCE_OUT -> bounceOut(t);
            case BOUNCE_IN_OUT -> t < 0.5f
                    ? (1.0f - bounceOut(1.0f - 2.0f * t)) * 0.5f
                    : (1.0f + bounceOut(2.0f * t - 1.0f)) * 0.5f;
        };
    }

    private static float sq(float v) {
        return v * v;
    }

    private static float cube(float v) {
        return v * v * v;
    }

    private static float bounceOut(float t) {
        if (t < 1.0f / BOUNCE_D) {
            return BOUNCE_N * t * t;
        }
        if (t < 2.0f / BOUNCE_D) {
            float v = t - 1.5f / BOUNCE_D;
            return BOUNCE_N * v * v + 0.75f;
        }
        if (t < 2.5f / BOUNCE_D) {
            float v = t - 2.25f / BOUNCE_D;
            return BOUNCE_N * v * v + 0.9375f;
        }
        float v = t - 2.625f / BOUNCE_D;
        return BOUNCE_N * v * v + 0.984375f;
    }
}
