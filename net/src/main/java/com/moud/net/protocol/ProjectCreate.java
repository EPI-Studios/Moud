package com.moud.net.protocol;

public record ProjectCreate(
        long requestId,
        String name,
        String author
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.PROJECT_CREATE;
    }
}

