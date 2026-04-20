package com.moud.net.protocol;


public record PlayReady(String sceneId) implements Message {
    @Override
    public MessageType type() {
        return MessageType.PLAY_READY;
    }
}
