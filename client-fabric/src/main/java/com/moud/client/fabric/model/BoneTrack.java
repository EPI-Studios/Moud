package com.moud.client.fabric.model;

public final class BoneTrack {
    private final float[] posTimes,  posX,   posY,   posZ;
    private final float[] rotTimes,  rotX,   rotY,   rotZ;
    private final float[] scaleTimes, scaleX, scaleY, scaleZ;

    public BoneTrack(float[] posTimes,   float[] posX,    float[] posY,    float[] posZ,
                     float[] rotTimes,   float[] rotX,    float[] rotY,    float[] rotZ,
                     float[] scaleTimes, float[] scaleX,  float[] scaleY,  float[] scaleZ) {
        this.posTimes   = posTimes;  this.posX   = posX;   this.posY   = posY;   this.posZ   = posZ;
        this.rotTimes   = rotTimes;  this.rotX   = rotX;   this.rotY   = rotY;   this.rotZ   = rotZ;
        this.scaleTimes = scaleTimes; this.scaleX = scaleX; this.scaleY = scaleY; this.scaleZ = scaleZ;
    }

    public float posX(float t)   { return lerp(posTimes,   posX,   t, 0f); }
    public float posY(float t)   { return lerp(posTimes,   posY,   t, 0f); }
    public float posZ(float t)   { return lerp(posTimes,   posZ,   t, 0f); }
    public float rotX(float t)   { return lerp(rotTimes,   rotX,   t, 0f); }
    public float rotY(float t)   { return lerp(rotTimes,   rotY,   t, 0f); }
    public float rotZ(float t)   { return lerp(rotTimes,   rotZ,   t, 0f); }
    public float scaleX(float t) { return lerp(scaleTimes, scaleX, t, 1f); }
    public float scaleY(float t) { return lerp(scaleTimes, scaleY, t, 1f); }
    public float scaleZ(float t) { return lerp(scaleTimes, scaleZ, t, 1f); }

    private static float lerp(float[] times, float[] vals, float t, float def) {
        if (times == null || times.length == 0) return def;
        if (t <= times[0]) return vals[0];
        int n = times.length;
        if (t >= times[n - 1]) return vals[n - 1];
        // binary search for the enclosing segment
        int lo = 0, hi = n - 2;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (times[mid] <= t) lo = mid; else hi = mid - 1;
        }
        float alpha = (t - times[lo]) / (times[lo + 1] - times[lo]);
        return vals[lo] + alpha * (vals[lo + 1] - vals[lo]);
    }
}
