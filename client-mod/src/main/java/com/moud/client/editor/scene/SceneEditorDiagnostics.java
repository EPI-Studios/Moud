package com.moud.client.editor.scene;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class SceneEditorDiagnostics {
    private static final int MAX_LINES = 200;
    private static final Deque<String> LINES = new ArrayDeque<>(MAX_LINES);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private SceneEditorDiagnostics() {}

    public static void log(String message) {
        String line = "[" + FORMATTER.format(Instant.now()) + "] " + message;
        synchronized (LINES) {
            if (LINES.size() >= MAX_LINES) {
                LINES.removeFirst();
            }
            LINES.addLast(line);
        }
    }

    public static List<String> snapshot() {
        synchronized (LINES) {
            return List.copyOf(LINES);
        }
    }

    public static void clear() {
        synchronized (LINES) {
            LINES.clear();
        }
    }
}
