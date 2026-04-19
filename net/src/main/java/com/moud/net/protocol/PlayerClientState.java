package com.moud.net.protocol;

public record PlayerClientState(
        String playerUuid,
        String key,
        String value
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.PLAYER_CLIENT_STATE;
    }
}
