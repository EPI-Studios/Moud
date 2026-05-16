package com.moud.client.fabric.scene;

public final class Transform3DCell {
    public double px, py, pz;
    public double qx, qy, qz;
    public double qw = 1.0;
    public double sx = 1.0, sy = 1.0, sz = 1.0;

    public boolean hasPosition;
    public boolean hasRotation;
    public boolean hasScale;

    private long localEpoch = 1L;

    public long localEpoch() { return localEpoch; }

    public void bumpEpoch() {
        long next = localEpoch + 1L;
        localEpoch = next <= 0L ? 1L : next;
    }

    public void setPosition(double x, double y, double z) {
        px = x; py = y; pz = z;
        hasPosition = true;
        bumpEpoch();
    }

    public void setPositionComponent(int axis, double value) {
        switch (axis) {
            case 0 -> px = value;
            case 1 -> py = value;
            case 2 -> pz = value;
            default -> { return; }
        }
        hasPosition = true;
        bumpEpoch();
    }

    public void setQuaternion(double x, double y, double z, double w) {
        qx = x; qy = y; qz = z; qw = w;
        hasRotation = true;
        bumpEpoch();
    }

    public void setScale(double x, double y, double z) {
        sx = x; sy = y; sz = z;
        hasScale = true;
        bumpEpoch();
    }

    public void setScaleComponent(int axis, double value) {
        switch (axis) {
            case 0 -> sx = value;
            case 1 -> sy = value;
            case 2 -> sz = value;
            default -> { return; }
        }
        hasScale = true;
        bumpEpoch();
    }

    public void clearPosition() {
        px = py = pz = 0.0;
        hasPosition = false;
        bumpEpoch();
    }

    public void clearRotation() {
        qx = qy = qz = 0.0;
        qw = 1.0;
        hasRotation = false;
        bumpEpoch();
    }

    public void clearScale() {
        sx = sy = sz = 1.0;
        hasScale = false;
        bumpEpoch();
    }
}
