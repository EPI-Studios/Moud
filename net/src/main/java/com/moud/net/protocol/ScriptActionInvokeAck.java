package com.moud.net.protocol;

public record ScriptActionInvokeAck(
        long requestId,
        long nodeId,
        boolean success,
        String error
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCRIPT_ACTION_INVOKE_ACK;
    }
}

