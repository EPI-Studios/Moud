package com.moud.client.fabric.editor.diagnostics;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class EditorDiagnostics {
    private static final int MAX_ENTRIES = 300;
    private static final Object LOCK = new Object();
    private static final ArrayList<Entry> ENTRIES = new ArrayList<>();
    private static long version;

    private EditorDiagnostics() {
    }

    public static void clear() {
        synchronized (LOCK) {
            ENTRIES.clear();
            version++;
        }
    }

    public static void info(String source, String message) {
        append(Severity.INFO, source, message);
    }

    public static void warn(String source, String message) {
        append(Severity.WARN, source, message);
    }

    public static void error(String source, String message) {
        append(Severity.ERROR, source, message);
    }

    public static Snapshot snapshot() {
        synchronized (LOCK) {
            return new Snapshot(version, List.copyOf(ENTRIES));
        }
    }

    private static void append(Severity severity, String source, String message) {
        synchronized (LOCK) {
            if (ENTRIES.size() >= MAX_ENTRIES) {
                ENTRIES.remove(0);
            }
            ENTRIES.add(new Entry(
                    System.currentTimeMillis(),
                    severity,
                    sanitize(source),
                    sanitize(message)
            ));
            version++;
        }
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    public record Snapshot(long version, List<Entry> entries) {
    }

    public record Entry(long timeMs, Severity severity, String source, String message) {
        public String formatted() {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date(timeMs));
            if (source == null || source.isBlank()) {
                return "[" + time + "] " + message;
            }
            return "[" + time + "] [" + source + "] " + message;
        }

        public String formattedTime() {
            return new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date(timeMs));
        }
    }

    public enum Severity {
        INFO,
        WARN,
        ERROR
    }
}
