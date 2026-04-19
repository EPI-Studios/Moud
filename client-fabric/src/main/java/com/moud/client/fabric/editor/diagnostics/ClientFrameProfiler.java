package com.moud.client.fabric.editor.diagnostics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientFrameProfiler {
    private static final int HISTORY = 360;
    private static final int MAX_SCOPES = 64;
    private static final int MAX_STACK_DEPTH = 32;
    private static final Object LOCK = new Object();

    private static final float[] frameTimesMs = new float[HISTORY];
    private static final float[][] scopeHistoryMs = new float[MAX_SCOPES][HISTORY];
    private static final long[] currentScopeNanos = new long[MAX_SCOPES];
    private static final int[] scopeStack = new int[MAX_STACK_DEPTH];
    private static final long[] scopeStartNanos = new long[MAX_STACK_DEPTH];
    private static final String[] scopeNames = new String[MAX_SCOPES];
    private static final Map<String, Integer> scopeIds = new HashMap<>();

    private static int frameCursor;
    private static int frameCount;
    private static int scopeCount;
    private static int stackDepth;
    private static boolean frameActive;
    private static long frameStartNanos;
    private static volatile int version;

    private ClientFrameProfiler() {
    }

    public static void beginFrame() {
        synchronized (LOCK) {
            if (frameActive) {
                finishFrame(System.nanoTime());
            }
            frameActive = true;
            frameStartNanos = System.nanoTime();
            stackDepth = 0;
            Arrays.fill(currentScopeNanos, 0L);
        }
    }

    public static void endFrame() {
        synchronized (LOCK) {
            if (!frameActive) {
                return;
            }
            finishFrame(System.nanoTime());
        }
    }

    public static void beginScope(String name) {
        synchronized (LOCK) {
            if (!frameActive || name == null || name.isBlank() || stackDepth >= MAX_STACK_DEPTH) {
                return;
            }
            int id = scopeId(name);
            if (id < 0) {
                return;
            }
            scopeStack[stackDepth] = id;
            scopeStartNanos[stackDepth] = System.nanoTime();
            stackDepth++;
        }
    }

    public static void endScope() {
        synchronized (LOCK) {
            if (!frameActive || stackDepth <= 0) {
                return;
            }
            long now = System.nanoTime();
            int index = --stackDepth;
            int scopeId = scopeStack[index];
            long start = scopeStartNanos[index];
            if (scopeId >= 0 && scopeId < MAX_SCOPES && now > start) {
                currentScopeNanos[scopeId] += now - start;
            }
        }
    }

    public static Scope scope(String name) {
        beginScope(name);
        return Scope.INSTANCE;
    }

    public static Snapshot snapshot() {
        synchronized (LOCK) {
            int count = frameCount;
            float[] frames = new float[count];
            for (int i = 0; i < count; i++) {
                frames[i] = frameTimesMs[historyIndex(i, count)];
            }

            ArrayList<ScopeStat> stats = new ArrayList<>();
            for (int scopeIndex = 0; scopeIndex < scopeCount; scopeIndex++) {
                float[] samples = new float[count];
                float sum = 0.0f;
                float max = 0.0f;
                for (int i = 0; i < count; i++) {
                    float value = scopeHistoryMs[scopeIndex][historyIndex(i, count)];
                    samples[i] = value;
                    sum += value;
                    if (value > max) {
                        max = value;
                    }
                }
                if (max <= 0.0f) {
                    continue;
                }
                float avg = count > 0 ? sum / count : 0.0f;
                float last = count > 0 ? samples[count - 1] : 0.0f;
                float p95 = percentile(samples, 0.95f);
                stats.add(new ScopeStat(scopeNames[scopeIndex], samples, avg, p95, max, last));
            }
            stats.sort(Comparator.comparingDouble(ScopeStat::avgMs).reversed());
            return new Snapshot(frames, List.copyOf(stats), version);
        }
    }

    private static void finishFrame(long now) {
        while (stackDepth > 0) {
            int index = --stackDepth;
            int scopeId = scopeStack[index];
            long start = scopeStartNanos[index];
            if (scopeId >= 0 && scopeId < MAX_SCOPES && now > start) {
                currentScopeNanos[scopeId] += now - start;
            }
        }

        int frameIndex = frameCursor % HISTORY;
        float frameMs = Math.max(0.0f, (now - frameStartNanos) / 1_000_000.0f);
        frameTimesMs[frameIndex] = frameMs;
        for (int scopeIndex = 0; scopeIndex < MAX_SCOPES; scopeIndex++) {
            scopeHistoryMs[scopeIndex][frameIndex] = currentScopeNanos[scopeIndex] / 1_000_000.0f;
        }
        frameCursor++;
        frameCount = Math.min(frameCount + 1, HISTORY);
        frameActive = false;
        version++;
    }

    private static int scopeId(String name) {
        Integer existing = scopeIds.get(name);
        if (existing != null) {
            return existing;
        }
        if (scopeCount >= MAX_SCOPES) {
            return -1;
        }
        int next = scopeCount++;
        scopeIds.put(name, next);
        scopeNames[next] = name;
        return next;
    }

    private static int historyIndex(int orderedIndex, int count) {
        return (frameCursor - count + orderedIndex + HISTORY) % HISTORY;
    }

    private static float percentile(float[] values, float p) {
        if (values.length == 0) {
            return 0.0f;
        }
        float[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        int index = Math.min(sorted.length - 1, Math.max(0, (int) Math.ceil(p * sorted.length) - 1));
        return sorted[index];
    }

    public record Snapshot(float[] frameTimes, List<ScopeStat> scopes, int version) {
        public float avg() {
            if (frameTimes.length == 0) {
                return 0.0f;
            }
            float sum = 0.0f;
            for (float f : frameTimes) {
                sum += f;
            }
            return sum / frameTimes.length;
        }

        public float max() {
            float max = 0.0f;
            for (float f : frameTimes) {
                if (f > max) {
                    max = f;
                }
            }
            return max;
        }

        public float p95() {
            return percentile(frameTimes, 0.95f);
        }

        public float p99() {
            return percentile(frameTimes, 0.99f);
        }

        public float fps() {
            float avg = avg();
            return avg > 0.0f ? 1000.0f / avg : 0.0f;
        }

        public float latestFrameMs() {
            return frameTimes.length == 0 ? 0.0f : frameTimes[frameTimes.length - 1];
        }
    }

    public record ScopeStat(String name, float[] samples, float avgMs, float p95Ms, float maxMs, float lastMs) {
    }

    public enum Scope implements AutoCloseable {
        INSTANCE;

        @Override
        public void close() {
            endScope();
        }
    }
}
