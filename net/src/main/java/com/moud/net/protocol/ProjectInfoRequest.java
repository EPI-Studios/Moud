package com.moud.net.protocol;


public record ProjectInfoRequest(long requestId) implements Message {
    @Override

    public MessageType type() {
        return MessageType.PROJECT_INFO_REQUEST;
    }
}

