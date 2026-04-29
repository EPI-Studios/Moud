package com.moud.net.protocol;

public record MatchmakerStatus(String code, String label, boolean show) implements Message {
    @Override
    public MessageType type() {
        return MessageType.MATCHMAKER_STATUS;
    }
}
