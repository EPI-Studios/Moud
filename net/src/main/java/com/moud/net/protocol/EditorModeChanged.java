package com.moud.net.protocol;

public record EditorModeChanged(boolean editorOpen) implements Message {
    @Override
    public MessageType type() {
        return MessageType.EDITOR_MODE_CHANGED;
    }
}

