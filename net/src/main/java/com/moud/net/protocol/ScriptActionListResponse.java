package com.moud.net.protocol;


import java.util.List;

public record ScriptActionListResponse(
        long requestId,
        long nodeId,
        boolean success,
        String error,
        List<String> actions
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.SCRIPT_ACTION_LIST_RESPONSE;
    }
}

