package com.moud.net.protocol;

public record PlayerInput(
        long clientTick,
        float moveX,
        float moveZ,
        float yawDeg,
        float pitchDeg,
        boolean jump,
        boolean sprint
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.PLAYER_INPUT;
    }
}

