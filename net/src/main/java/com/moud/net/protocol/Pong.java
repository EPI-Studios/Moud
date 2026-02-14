package com.moud.net.protocol;

public record Pong(long nonce) implements Message {
    @Override
    public MessageType type() {
        return MessageType.PONG;
    }
}
