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
        float fogColorR,
        float fogColorG,
        float fogColorB,
        float fogDensity,
        int timeTicks,
        String weather,
        float ambientLight
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.RUNTIME_STATE;
    }
}
