package com.moud.net.protocol;


public record SceneCreate(String sceneId, String displayName) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCENE_CREATE;
    }
}

