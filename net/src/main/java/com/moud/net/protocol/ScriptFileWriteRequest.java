package com.moud.net.protocol;


public record ScriptFileWriteRequest(long requestId, String path, String content) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCRIPT_FILE_WRITE_REQUEST;
    }
}

