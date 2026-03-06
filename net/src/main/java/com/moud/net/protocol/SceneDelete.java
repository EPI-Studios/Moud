package com.moud.net.protocol;


public record SceneDelete(String sceneId) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCENE_DELETE;
    }
}

