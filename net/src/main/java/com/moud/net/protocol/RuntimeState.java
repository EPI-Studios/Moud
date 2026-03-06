package com.moud.net.protocol;

public record RuntimeState(
        long serverTick,
        String sceneId,
        boolean fogEnabled,
        float fogColorR,
        float fogColorG,
        float fogColorB,
        float fogDensity,
        int timeTicks,
        String weather,
        float ambientLight,
        boolean useSceneCamera,
        float sceneCamX,
        float sceneCamY,
        float sceneCamZ,
        float sceneCamYawDeg,
        float sceneCamPitchDeg,
        float sceneCamRollDeg
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.RUNTIME_STATE;
    }
}
