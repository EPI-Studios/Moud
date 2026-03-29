package com.moud.net.protocol;

public record UiNodeEvent(long nodeId, String event, float value) implements Message {
    @Override
    public MessageType type() {
        return MessageType.UI_NODE_EVENT;
    }
}
