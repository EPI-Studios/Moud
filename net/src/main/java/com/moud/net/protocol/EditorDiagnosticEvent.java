package com.moud.net.protocol;

public record EditorDiagnosticEvent(
        String severity,
        String source,
        String message
) implements Message {
    @Override
    public MessageType type() {
        return MessageType.EDITOR_DIAGNOSTIC_EVENT;
    }
}
