package com.moud.net.protocol;

public record PlayerMotion(
        int mode,
        float x,
        float y,
        float z,
        float yawDeg
) implements Message {

    public static final int MODE_VELOCITY = 0;
    public static final int MODE_ANCHOR_SET = 1;
    public static final int MODE_ANCHOR_CLEAR = 2;
    public static final int MODE_VELOCITY_REPORT = 3;

    public static PlayerMotion velocity(float vx, float vy, float vz) {
        return new PlayerMotion(MODE_VELOCITY, vx, vy, vz, 0f);
    }

    public static PlayerMotion anchorSet(float x, float y, float z, float yawDeg) {
        return new PlayerMotion(MODE_ANCHOR_SET, x, y, z, yawDeg);
    }

    public static PlayerMotion anchorClear() {
        return new PlayerMotion(MODE_ANCHOR_CLEAR, 0f, 0f, 0f, 0f);
    }

    public static PlayerMotion velocityReport(float vx, float vy, float vz) {
        return new PlayerMotion(MODE_VELOCITY_REPORT, vx, vy, vz, 0f);
    }

    @Override
    public MessageType type() {
        return MessageType.PLAYER_MOTION;
    }
}