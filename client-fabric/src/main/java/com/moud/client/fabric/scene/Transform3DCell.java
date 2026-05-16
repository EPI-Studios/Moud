package com.moud.client.fabric.scene;

import com.moud.core.math.Quat;

public final class Transform3DCell {
    public double px, py, pz;
    public double qx, qy, qz;
    public double qw = 1.0;
    public double euX, euY, euZ;
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
        euX = euY = euZ = 0.0;
        hasRotation = false;
        bumpEpoch();
    }

    public void setEulerComponent(int axis, double degrees) {
        switch (axis) {
            case 0 -> euX = degrees;
            case 1 -> euY = degrees;
            case 2 -> euZ = degrees;
            default -> { return; }
        }
        bakeEuler();
        hasRotation = true;
        bumpEpoch();
    }

    public void clearEulerComponent(int axis) {
        switch (axis) {
            case 0 -> euX = 0.0;
            case 1 -> euY = 0.0;
            case 2 -> euZ = 0.0;
            default -> { return; }
        }
        bakeEuler();
        boolean stillRotated = euX != 0.0 || euY != 0.0 || euZ != 0.0;
        hasRotation = stillRotated;
        if (!stillRotated) {
            qx = qy = qz = 0.0;
            qw = 1.0;
        }
        bumpEpoch();
    }

    public void clearPositionComponent(int axis) {
        switch (axis) {
            case 0 -> px = 0.0;
            case 1 -> py = 0.0;
            case 2 -> pz = 0.0;
            default -> { return; }
        }
        boolean stillSet = px != 0.0 || py != 0.0 || pz != 0.0;
        hasPosition = stillSet;
        bumpEpoch();
    }

    public void clearScaleComponent(int axis) {
        switch (axis) {
            case 0 -> sx = 1.0;
            case 1 -> sy = 1.0;
            case 2 -> sz = 1.0;
            default -> { return; }
        }
        boolean stillSet = sx != 1.0 || sy != 1.0 || sz != 1.0;
        hasScale = stillSet;
        bumpEpoch();
    }

    public void resetTransform() {
        px = py = pz = 0.0;
        qx = qy = qz = 0.0;
        qw = 1.0;
        euX = euY = euZ = 0.0;
        sx = sy = sz = 1.0;
        hasPosition = false;
        hasRotation = false;
        hasScale = false;
        bumpEpoch();
    }

    public void setEuler(double xDeg, double yDeg, double zDeg) {
        euX = xDeg; euY = yDeg; euZ = zDeg;
        bakeEuler();
        hasRotation = true;
        bumpEpoch();
    }

    private void bakeEuler() {
        Quat q = Quat.fromEulerDeg((float) euX, (float) euY, (float) euZ);
        qx = q.x(); qy = q.y(); qz = q.z(); qw = q.w();
    }

    public void clearScale() {
        sx = sy = sz = 1.0;
        hasScale = false;
        bumpEpoch();
    }
}
