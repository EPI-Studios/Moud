package com.moud.client.fabric.editor.diagnostics;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class ClientNetworkLog {
    private static final int MAX = 300;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final List<Entry> entries = new ArrayList<>();
    private static volatile int version;
    private static long totalSentBytes;
    private static long totalRecvBytes;

    private ClientNetworkLog() {}

    public static synchronized void recordSend(String lane, int bytes) {
        if (entries.size() >= MAX) entries.remove(0);
        entries.add(new Entry(System.currentTimeMillis(), Direction.SEND, lane, bytes));
        totalSentBytes += bytes;
        version++;
    }

    public static synchronized void recordRecv(String lane, int bytes) {
        if (entries.size() >= MAX) entries.remove(0);
        entries.add(new Entry(System.currentTimeMillis(), Direction.RECV, lane, bytes));
        totalRecvBytes += bytes;
        version++;
    }

    public static synchronized void clear() {
        entries.clear();
        totalSentBytes = 0;
        totalRecvBytes = 0;
        version++;
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(List.copyOf(entries), totalSentBytes, totalRecvBytes, version);
    }

    public enum Direction { SEND, RECV }

    public record Snapshot(List<Entry> entries, long totalSentBytes, long totalRecvBytes, int version) {}

    public record Entry(long timeMs, Direction direction, String lane, int bytes) {
        public String formattedTime() {
            return LocalTime.ofSecondOfDay((timeMs / 1000) % 86400).format(FMT);
        }

        public String sizeLabel() {
            if (bytes < 1024) return bytes + "B";
            return String.format("%.1fK", bytes / 1024.0);
        }
    }
}
