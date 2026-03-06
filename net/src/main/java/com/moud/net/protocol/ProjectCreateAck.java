package com.moud.net.protocol;


public record ProjectCreateAck(
        long requestId,
        boolean success,
        String error,
        String name,
        String author
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.PROJECT_CREATE_ACK;
    }
}

