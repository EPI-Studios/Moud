package com.moud.net.protocol;


public record ProjectInfo(
        long requestId,
        boolean exists,
        String name,
        String author
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.PROJECT_INFO;
    }
}

