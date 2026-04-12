package com.moud.client.fabric.runtime;

public final class ClientCameraState {

    public float posX = Float.NaN;
    public float posY = Float.NaN;
    public float posZ = Float.NaN;

    public float yaw   = Float.NaN;
    public float pitch = Float.NaN;
    public float roll  = Float.NaN;

    public float fov = -1f;

    public boolean captureMouseEnabled;
    public float mouseDx;
    public float mouseDy;
    public float cursorX = Float.NaN;
    public float cursorY = Float.NaN;
    public float prevCursorX = Float.NaN;
    public float prevCursorY = Float.NaN;

    public float playerYaw = Float.NaN;

    public boolean hasOverride;

    private float prevFrameYaw   = 0f;
    private float prevFramePitch = 0f;
    private float prevFrameRoll  = 0f;
    private float prevFrameFov   = 70f;

    public void resetForFrame() {
        if (!Float.isNaN(yaw))   prevFrameYaw   = yaw;
        if (!Float.isNaN(pitch)) prevFramePitch = pitch;
        if (!Float.isNaN(roll))  prevFrameRoll  = roll;
        if (fov > 0)             prevFrameFov   = fov;

        hasOverride = false;
        posX = posY = posZ = Float.NaN;
        yaw = pitch = roll = Float.NaN;
        fov = -1f;
        playerYaw = Float.NaN;
    }

    public float getYawOrPrev()   { return Float.isNaN(yaw)   ? prevFrameYaw   : yaw; }
    public float getPitchOrPrev() { return Float.isNaN(pitch) ? prevFramePitch : pitch; }
    public float getRollOrPrev()  { return Float.isNaN(roll)  ? prevFrameRoll  : roll; }
    public float getFovOrPrev()   { return fov > 0            ? fov            : prevFrameFov; }
}
