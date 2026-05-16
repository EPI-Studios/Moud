package com.moud.client.fabric.runtime;

public final class MoudPlayer {
    private double x, y, z;
    private double prevX, prevY, prevZ;
    private double vx, vy, vz;
    private float yaw, pitch, bodyYaw, headYaw, rotationZ;
    private float radius = 0.3f, height = 1.8f;
    private boolean onFloor, onWall, onCeiling;
    private boolean justLanded, justLeftFloor;
    private long poseEpoch;
    private boolean active;

    public void writePose(double x, double y, double z,
                          float yaw, float pitch, float bodyYaw, float headYaw, float rotationZ) {
        this.prevX = this.x;
        this.prevY = this.y;
        this.prevZ = this.z;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.bodyYaw = bodyYaw;
        this.headYaw = headYaw;
        this.rotationZ = rotationZ;
        this.poseEpoch++;
        this.active = true;
    }

    public void writeVelocity(double vx, double vy, double vz) {
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
    }

    public void writeShape(float radius, float height) {
        this.radius = radius;
        this.height = height;
    }

    public void writeGroundState(boolean onFloor, boolean onWall, boolean onCeiling,
                                 boolean justLanded, boolean justLeftFloor) {
        this.onFloor = onFloor;
        this.onWall = onWall;
        this.onCeiling = onCeiling;
        this.justLanded = justLanded;
        this.justLeftFloor = justLeftFloor;
    }

    public void reset() {
        x = y = z = prevX = prevY = prevZ = 0.0;
        vx = vy = vz = 0.0;
        yaw = pitch = bodyYaw = headYaw = rotationZ = 0f;
        radius = 0.3f;
        height = 1.8f;
        onFloor = onWall = onCeiling = false;
        justLanded = justLeftFloor = false;
        poseEpoch = 0L;
        active = false;
    }

    public boolean isActive() { return active; }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public double prevX() { return prevX; }
    public double prevY() { return prevY; }
    public double prevZ() { return prevZ; }

    public double vx() { return vx; }
    public double vy() { return vy; }
    public double vz() { return vz; }

    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public float bodyYaw() { return bodyYaw; }
    public float headYaw() { return headYaw; }
    public float rotationZ() { return rotationZ; }

    public float radius() { return radius; }
    public float height() { return height; }
    public float eyeY() { return (float) (y + height * 0.9f); }

    public boolean onFloor() { return onFloor; }
    public boolean onWall() { return onWall; }
    public boolean onCeiling() { return onCeiling; }
    public boolean justLanded() { return justLanded; }
    public boolean justLeftFloor() { return justLeftFloor; }

    public long poseEpoch() { return poseEpoch; }
}
