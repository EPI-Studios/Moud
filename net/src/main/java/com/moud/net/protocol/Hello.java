package com.moud.net.protocol;

public record Hello(int protocolVersion) implements Message {
    @Override
    public MessageType type() {
        return MessageType.HELLO;
    }
}
