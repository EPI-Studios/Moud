package com.moud.net.protocol;

public record RuntimeState(
        long serverTick,
        long lastProcessedTick,
        String sceneId,
        float charX,
        float charY,
        float charZ,
        float velX,
        float velY,
        float velZ,
        boolean onFloor,
        float camYawDeg,
        float camPitchDeg,
        boolean fogEnabled,
        String fogColor,
        float fogDensity
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.RUNTIME_STATE;
    }
}
