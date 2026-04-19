package com.moud.client.fabric.editor.diagnostics;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class ClientOutput {
    private static final int MAX = 500;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final List<Entry> entries = new ArrayList<>();
    private static volatile int version;

    private ClientOutput() {}

    public static synchronized void print(String source, String message) {
        if (entries.size() >= MAX) entries.remove(0);
        entries.add(new Entry(System.currentTimeMillis(), source, message));
        version++;
    }

    public static synchronized void clear() {
        entries.clear();
        version++;
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(List.copyOf(entries), version);
    }

    public record Snapshot(List<Entry> entries, int version) {}

    public record Entry(long timeMs, String source, String message) {
        public String formattedTime() {
            return LocalTime.ofSecondOfDay((timeMs / 1000) % 86400).format(FMT);
        }
    }
}
