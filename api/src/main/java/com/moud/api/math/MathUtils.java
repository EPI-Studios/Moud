package com.moud.api.math;

public class MathUtils {

    public static float lerp(float source, float destination, float smoothingFactor, float deltaTime) {
        return source + (1.0f - (float) Math.pow(smoothingFactor, deltaTime)) * (destination - source);
    }

    public static Vector3 lerp(Vector3 source, Vector3 destination, float smoothingFactor, float deltaTime) {
        float t = 1.0f - (float) Math.pow(smoothingFactor, deltaTime);
        return new Vector3(
                source.x + t * (destination.x - source.x),
                source.y + t * (destination.y - source.y),
                source.z + t * (destination.z - source.z)
        );
    }

    public static Quaternion slerp(Quaternion source, Quaternion destination, float smoothingFactor, float deltaTime) {
        float t = 1.0f - (float) Math.pow(smoothingFactor, deltaTime);
        return source.slerp(destination, t);
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static Vector3 clamp(Vector3 value, Vector3 min, Vector3 max) {
        return new Vector3(
                clamp(value.x, min.x, max.x),
                clamp(value.y, min.y, max.y),
                clamp(value.z, min.z, max.z)
        );
    }

    public static float deltaAngle(float current, float target) {
        float delta = repeat(target - current, 360.0f);
        if (delta > 180.0f) {
            delta -= 360.0f;
        }
        return delta;
    }

    public static float repeat(float value, float length) {
        return value - (float) Math.floor(value / length) * length;
    }

    public static float lerpAngle(float current, float target, float t) {
        return current + deltaAngle(current, target) * t;
    }

    public static float smoothDamp(float current, float target, float currentVelocity, float smoothTime, float deltaTime) {
        float omega = 2.0f / smoothTime;
        float x = omega * deltaTime;
        float exp = 1.0f / (1.0f + x + 0.48f * x * x + 0.235f * x * x * x);
        float change = current - target;
        float originalTo = target;

        float maxChange = Float.MAX_VALUE * smoothTime;
        change = clamp(change, -maxChange, maxChange);
        target = current - change;

        float temp = (currentVelocity + omega * change) * deltaTime;
        currentVelocity = (currentVelocity - omega * temp) * exp;
        float output = target + (change + temp) * exp;

        if (originalTo - current > 0.0f == output > originalTo) {
            output = originalTo;
            currentVelocity = (output - originalTo) / deltaTime;
        }

        return output;
    }

    public static float deg2Rad(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    public static float rad2Deg(float radians) {
        return (float) Math.toDegrees(radians);
    }
}