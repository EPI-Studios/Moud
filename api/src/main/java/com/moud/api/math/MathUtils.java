package com.moud.api.math;

import org.graalvm.polyglot.HostAccess;

public final class MathUtils {
    @HostAccess.Export
    public static final float EPSILON = 1e-6f;
    @HostAccess.Export
    public static final float PI = (float) Math.PI;
    @HostAccess.Export
    public static final float TWO_PI = (float) (2.0 * Math.PI);
    @HostAccess.Export
    public static final float HALF_PI = (float) (Math.PI / 2.0);
    @HostAccess.Export
    public static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    @HostAccess.Export
    public static final float RAD_TO_DEG = (float) (180.0 / Math.PI);

    private MathUtils() {}

    @HostAccess.Export
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @HostAccess.Export
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @HostAccess.Export
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @HostAccess.Export
    public static float inverseLerp(float a, float b, float value) {
        if (Math.abs(b - a) < EPSILON) {
            return 0.0f;
        }
        return (value - a) / (b - a);
    }

    @HostAccess.Export
    public static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    @HostAccess.Export
    public static float smootherstep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }

    @HostAccess.Export
    public static float map(float value, float inMin, float inMax, float outMin, float outMax) {
        return outMin + (outMax - outMin) * inverseLerp(inMin, inMax, value);
    }

    @HostAccess.Export
    public static float wrap(float value, float min, float max) {
        float range = max - min;
        if (range <= 0.0f) {
            return min;
        }
        return value - range * (float) Math.floor((value - min) / range);
    }

    @HostAccess.Export
    public static float pingPong(float t, float length) {
        t = repeat(t, length * 2.0f);
        return length - Math.abs(t - length);
    }

    @HostAccess.Export
    public static float repeat(float t, float length) {
        return clamp(t - (float) Math.floor(t / length) * length, 0.0f, length);
    }

    @HostAccess.Export
    public static float deltaAngle(float current, float target) {
        float delta = wrap(target - current, -180.0f, 180.0f);
        return delta > 180.0f ? delta - 360.0f : delta;
    }

    @HostAccess.Export
    public static float lerpAngle(float a, float b, float t) {
        return a + deltaAngle(a, b) * clamp(t, 0.0f, 1.0f);
    }

    @HostAccess.Export
    public static float moveTowards(float current, float target, float maxDelta) {
        if (Math.abs(target - current) <= maxDelta) {
            return target;
        }
        return current + Math.copySign(maxDelta, target - current);
    }

    @HostAccess.Export
    public static float moveTowardsAngle(float current, float target, float maxDelta) {
        float deltaAngle = deltaAngle(current, target);
        if (-maxDelta < deltaAngle && deltaAngle < maxDelta) {
            return target;
        }
        target = current + deltaAngle;
        return moveTowards(current, target, maxDelta);
    }

    @HostAccess.Export
    public static float smoothDamp(float current, float target, float currentVelocity, float smoothTime, float maxSpeed, float deltaTime) {
        smoothTime = Math.max(0.0001f, smoothTime);
        float omega = 2.0f / smoothTime;
        float x = omega * deltaTime;
        float exp = 1.0f / (1.0f + x + 0.48f * x * x + 0.235f * x * x * x);
        float change = current - target;
        float originalTo = target;

        float maxChange = maxSpeed * smoothTime;
        change = clamp(change, -maxChange, maxChange);
        target = current - change;

        float temp = (currentVelocity + omega * change) * deltaTime;
        float newVelocity = (currentVelocity - omega * temp) * exp;
        float output = target + (change + temp) * exp;

        if (originalTo - current > 0.0f == output > originalTo) {
            output = originalTo;
            newVelocity = (output - originalTo) / deltaTime;
        }

        return output;
    }

    @HostAccess.Export
    public static float sign(float value) {
        return value >= 0.0f ? 1.0f : -1.0f;
    }

    @HostAccess.Export
    public static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    @HostAccess.Export
    public static float pow(float base, float exponent) {
        return (float) Math.pow(base, exponent);
    }

    @HostAccess.Export
    public static float sqrt(float value) {
        return (float) Math.sqrt(value);
    }

    @HostAccess.Export
    public static float sin(float radians) {
        return (float) Math.sin(radians);
    }

    @HostAccess.Export
    public static float cos(float radians) {
        return (float) Math.cos(radians);
    }

    @HostAccess.Export
    public static float tan(float radians) {
        return (float) Math.tan(radians);
    }

    @HostAccess.Export
    public static float asin(float value) {
        return (float) Math.asin(value);
    }

    @HostAccess.Export
    public static float acos(float value) {
        return (float) Math.acos(value);
    }

    @HostAccess.Export
    public static float atan(float value) {
        return (float) Math.atan(value);
    }

    @HostAccess.Export
    public static float atan2(float y, float x) {
        return (float) Math.atan2(y, x);
    }

    @HostAccess.Export
    public static float floor(float value) {
        return (float) Math.floor(value);
    }

    @HostAccess.Export
    public static float ceil(float value) {
        return (float) Math.ceil(value);
    }

    @HostAccess.Export
    public static float round(float value) {
        return (float) Math.round(value);
    }

    @HostAccess.Export
    public static float abs(float value) {
        return Math.abs(value);
    }

    @HostAccess.Export
    public static float min(float a, float b) {
        return Math.min(a, b);
    }

    @HostAccess.Export
    public static float max(float a, float b) {
        return Math.max(a, b);
    }

    @HostAccess.Export
    public static boolean approximately(float a, float b) {
        return Math.abs(a - b) < EPSILON;
    }

    @HostAccess.Export
    public static boolean approximately(float a, float b, float tolerance) {
        return Math.abs(a - b) < tolerance;
    }

    @HostAccess.Export
    public static float toRadians(float degrees) {
        return degrees * DEG_TO_RAD;
    }

    @HostAccess.Export
    public static float toDegrees(float radians) {
        return radians * RAD_TO_DEG;
    }

    @HostAccess.Export
    public static float random() {
        return (float) Math.random();
    }

    @HostAccess.Export
    public static float random(float min, float max) {
        return min + (max - min) * random();
    }

    @HostAccess.Export
    public static int randomInt(int min, int max) {
        return min + (int) (Math.random() * (max - min + 1));
    }
}