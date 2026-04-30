package com.moud.client.fabric.scene.interp;

public final class SampledTransform {

    public float x;
    public float y;
    public float z;
    public float rx;
    public float ry;
    public float rz;
    public float sx = 1.0f;
    public float sy = 1.0f;
    public float sz = 1.0f;

    public boolean hasX;
    public boolean hasY;
    public boolean hasZ;
    public boolean hasRx;
    public boolean hasRy;
    public boolean hasRz;
    public boolean hasSx;
    public boolean hasSy;
    public boolean hasSz;

    public void reset() {
        x = y = z = rx = ry = rz = 0.0f;
        sx = sy = sz = 1.0f;
        hasX = hasY = hasZ = false;
        hasRx = hasRy = hasRz = false;
        hasSx = hasSy = hasSz = false;
    }
}
