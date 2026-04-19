package com.moud.net.protocol;

import java.util.Objects;

public record ScriptMessage(int direction, long nodeId, String topic, int flags, byte[] payload) implements Message {
    public static final int DIR_C2S = 0;
    public static final int DIR_S2C = 1;

    public static final int FLAG_RELIABLE = 1;

    public static final int MAX_PAYLOAD_BYTES = 1024;

    public ScriptMessage {
        Objects.requireNonNull(topic, "topic");
        if (payload == null) payload = new byte[0];
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payload too large: " + payload.length + " > " + MAX_PAYLOAD_BYTES);
        }
        if (direction != DIR_C2S && direction != DIR_S2C) {
            throw new IllegalArgumentException("invalid direction: " + direction);
        }
    }

    public boolean reliable() {
        return (flags & FLAG_RELIABLE) != 0;
    }

    @Override public MessageType type() { return MessageType.SCRIPT_MESSAGE; }
}
