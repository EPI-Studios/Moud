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
        float sceneCamRollDeg,
        boolean useFollowCamera,
        float followCamLocalX,
        float followCamLocalY,
        float followCamLocalZ,
        float followCamPitchDeg,
        float followCamRollDeg,
        boolean useScriptCamera,
        float scriptCamX,
        float scriptCamY,
        float scriptCamZ,
        float scriptCamYawDeg,
        float scriptCamPitchDeg,
        float scriptCamRollDeg
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.RUNTIME_STATE;
    }
}
