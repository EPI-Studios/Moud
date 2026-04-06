package com.moud.net.protocol;

public record CursorState(
        boolean cursorModeEnabled,
        boolean osCursorVisible
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.CURSOR_STATE;
    }
}
