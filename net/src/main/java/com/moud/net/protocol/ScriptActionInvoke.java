package com.moud.net.protocol;


public record ScriptActionInvoke(
        long requestId,
        long nodeId,
        String action
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCRIPT_ACTION_INVOKE;
    }
}

