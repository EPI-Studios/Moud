package com.moud.api.particle;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


public final class ParticleMath {
    private ParticleMath() {
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float evaluateScalarRamp(List<ScalarKeyframe> stops, float t) {
        if (stops == null || stops.isEmpty()) {
            return 0f;
        }
        if (stops.size() == 1) {
            return stops.get(0).value();
        }

        float clampedT = clamp(t, 0f, 1f);
        ScalarKeyframe previous = stops.get(0);
        for (int i = 1; i < stops.size(); i++) {
            ScalarKeyframe next = stops.get(i);
            if (clampedT <= next.t() || i == stops.size() - 1) {
                float segmentT = clamp((clampedT - previous.t()) / Math.max(1e-6f, next.t() - previous.t()), 0f, 1f);
                float eased = applyEase(segmentT, previous.ease());
                return lerp(previous.value(), next.value(), eased);
            }
            previous = next;
        }
        return stops.get(stops.size() - 1).value();
    }

    public static ColorSample evaluateColorRamp(List<ColorKeyframe> stops, float t) {
        if (stops == null || stops.isEmpty()) {
            return new ColorSample(1f, 1f, 1f, 1f);
        }
        if (stops.size() == 1) {
            ColorKeyframe single = stops.get(0);
            return new ColorSample(single.r(), single.g(), single.b(), single.a());
        }

        float clampedT = clamp(t, 0f, 1f);
        ColorKeyframe previous = stops.get(0);
        for (int i = 1; i < stops.size(); i++) {
            ColorKeyframe next = stops.get(i);
            if (clampedT <= next.t() || i == stops.size() - 1) {
                float segmentT = clamp((clampedT - previous.t()) / Math.max(1e-6f, next.t() - previous.t()), 0f, 1f);
                float eased = applyEase(segmentT, previous.ease());
                return new ColorSample(
                        lerp(previous.r(), next.r(), eased),
                        lerp(previous.g(), next.g(), eased),
                        lerp(previous.b(), next.b(), eased),
                        lerp(previous.a(), next.a(), eased)
                );
            }
            previous = next;
        }
        ColorKeyframe last = stops.get(stops.size() - 1);
        return new ColorSample(last.r(), last.g(), last.b(), last.a());
    }

    public static float sampleUniform(float min, float max) {
        return min + (max - min) * ThreadLocalRandom.current().nextFloat();
    }

    public static float sampleGaussian(float mean, float stdDev) {
        double u1 = ThreadLocalRandom.current().nextDouble();
        double u2 = ThreadLocalRandom.current().nextDouble();
        double mag = Math.sqrt(-2.0 * Math.log(Math.max(u1, 1e-12))) * Math.cos(2.0 * Math.PI * u2);
        return (float) (mean + stdDev * mag);
    }

    public static Vector3f jitterVector(Vector3f base, float magnitude) {
        double theta = sampleUniform(0f, (float) (2 * Math.PI));
        double phi = Math.acos(sampleUniform(-1f, 1f));
        double radius = magnitude * Math.cbrt(ThreadLocalRandom.current().nextDouble());

        double sinPhi = Math.sin(phi);
        return new Vector3f(
                (float) (base.x() + radius * sinPhi * Math.cos(theta)),
                (float) (base.y() + radius * Math.cos(phi)),
                (float) (base.z() + radius * sinPhi * Math.sin(theta))
        );
    }

    private static float applyEase(float t, Ease ease) {
        if (ease == null) {
            return t;
        }
        return switch (ease) {
            case EASE_IN -> t * t;
            case EASE_OUT -> 1f - (1f - t) * (1f - t);
            case EASE_IN_OUT -> t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2f) / 2f;
            case LINEAR -> t;
        };
    }

    public record ColorSample(float r, float g, float b, float a) {
    }
}
