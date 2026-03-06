package com.moud.net.protocol;

public record ScriptFileReadResponse(
        long requestId,
        boolean success,
        String path,
        String content,
        String error
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCRIPT_FILE_READ_RESPONSE;
    }
}

