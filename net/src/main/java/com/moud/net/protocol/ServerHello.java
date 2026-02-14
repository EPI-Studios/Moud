package com.moud.net.protocol;

public record ServerHello(int protocolVersion) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SERVER_HELLO;
    }
}
