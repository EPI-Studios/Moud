package com.moud.net.protocol;

public record SceneDeleteAck(String sceneId, boolean success, String error) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCENE_DELETE_ACK;
    }
}

