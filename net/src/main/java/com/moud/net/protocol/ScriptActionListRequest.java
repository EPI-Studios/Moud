package com.moud.net.protocol;

public record ScriptActionListRequest(
        long requestId,
        long nodeId
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCRIPT_ACTION_LIST_REQUEST;
    }
}

