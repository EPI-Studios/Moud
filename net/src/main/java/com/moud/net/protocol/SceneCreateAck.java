package com.moud.net.protocol;


public record SceneCreateAck(String sceneId, boolean success, String error) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCENE_CREATE_ACK;
    }
}

