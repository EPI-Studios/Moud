package com.moud.net.protocol;


public record ServerHello(int protocolVersion, boolean devMode) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SERVER_HELLO;
    }
}
