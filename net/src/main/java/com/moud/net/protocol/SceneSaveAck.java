package com.moud.net.protocol;

public record SceneSaveAck(String sceneId, boolean success, String error) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCENE_SAVE_ACK;
    }
}

