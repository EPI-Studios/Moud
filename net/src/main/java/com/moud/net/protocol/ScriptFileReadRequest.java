package com.moud.net.protocol;


public record ScriptFileReadRequest(long requestId, String path) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCRIPT_FILE_READ_REQUEST;
    }
}

