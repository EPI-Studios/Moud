package com.moud.net.protocol;

public record SceneSave(String sceneId) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCENE_SAVE;
    }
}

