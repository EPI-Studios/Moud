package com.meekdev.moud.mod.place;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class Output {

    public enum Level { INFO, WARN, ERROR, SYSTEM }

    public record Line(long sequence, long time, Level level, String side, String message) {}

    private static final int KEEP = 2000;
    private static final Deque<Line> LINES = new ArrayDeque<>();
    private static long next = 1;

    private Output() {}

    public static synchronized void add(Level level, String side, String message) {
        LINES.addLast(new Line(next++, System.currentTimeMillis(), level, side, message));
        while (LINES.size() > KEEP) LINES.removeFirst();
    }

    public static synchronized List<Line> since(long sequence) {
        List<Line> out = new ArrayList<>();
        for (Line line : LINES) if (line.sequence() > sequence) out.add(line);
        return out;
    }
}
