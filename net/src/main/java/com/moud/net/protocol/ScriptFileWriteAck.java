package com.moud.net.protocol;

public record ScriptFileWriteAck(
        long requestId,
        boolean success,
        String path,
        String error
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCRIPT_FILE_WRITE_ACK;
    }
}

