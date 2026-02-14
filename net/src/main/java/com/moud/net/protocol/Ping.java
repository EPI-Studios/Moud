package com.moud.net.protocol;

public record Ping(long nonce) implements Message {
    @Override
    public MessageType type() {
        return MessageType.PING;
    }
}
