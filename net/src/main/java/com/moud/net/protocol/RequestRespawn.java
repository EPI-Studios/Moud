package com.moud.net.protocol;

public record RequestRespawn() implements Message {
    @Override
    public MessageType type() {
        return MessageType.REQUEST_RESPAWN;
    }
}
