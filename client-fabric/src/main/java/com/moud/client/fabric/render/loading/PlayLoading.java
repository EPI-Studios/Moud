package com.moud.client.fabric.render.loading;

import com.moud.client.fabric.util.ClientDebugLog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PlayLoading {

    public record Entry(String id, String label) { }

    private static final long MIN_DURATION_MS = 800L;
    private static final long MAX_DURATION_MS = 30_000L;

    private static volatile boolean active = false;
    private static volatile boolean serverReady = false;
    private static volatile String activeSceneId = "";
    private static volatile String gameName = "";
    private static volatile long beginTimeMs = 0L;
    private static final List<Entry> queue = new CopyOnWriteArrayList<>();
    private static final List<Runnable> readyListeners = new CopyOnWriteArrayList<>();

    private PlayLoading() { }

    public static void begin() {
        if (active) return;
        active = true;
        serverReady = false;
        activeSceneId = "";
        beginTimeMs = System.currentTimeMillis();
        queue.clear();
    }

    public static void onServerReady(String sceneId) {
        serverReady = true;
        activeSceneId = sceneId == null ? "" : sceneId;
        maybeFinish();
    }

    public static void pushStatus(String id, String label) {
        if (id == null || id.isBlank() || label == null) return;
        queue.removeIf(e -> id.equals(e.id));
        queue.add(new Entry(id, label));
    }

    public static void popStatus(String id) {
        if (id == null) return;
        queue.removeIf(e -> id.equals(e.id));
        maybeFinish();
    }

    public static Entry currentStatus() {
        return queue.isEmpty() ? null : queue.get(queue.size() - 1);
    }

    public static boolean isServerReady() {
        return serverReady;
    }

    public static String currentSummary() {
        boolean ready = serverReady;
        int shaderCount = 0;
        int assetCount = 0;
        boolean serverPending = false;
        Entry other = null;
        for (Entry e : queue) {
            if (e == null || e.id == null) continue;
            if (e.id.startsWith("shader:")) shaderCount++;
            else if (e.id.startsWith("asset:")) assetCount++;
            else if (e.id.equals("server")) serverPending = true;
            else other = e;
        }
        if (shaderCount > 0) {
            return shaderCount == 1 ? "Compiling shader" : "Compiling shaders (" + shaderCount + ")";
        }
        if (assetCount > 0) {
            return assetCount == 1 ? "Loading asset" : "Loading assets (" + assetCount + ")";
        }
        if (other != null) {
            return other.label;
        }
        if (serverPending) {
            return ready ? "Finalizing scene" : "Waiting for server to restore scene";
        }
        return ready ? "Ready" : "Preparing";
    }

    public static boolean isActive() {
        if (!active) return false;
        long elapsed = System.currentTimeMillis() - beginTimeMs;
        boolean workDone = serverReady && queue.isEmpty();
        if (!workDone) {
            if (elapsed >= MAX_DURATION_MS) {
                queue.clear();
                serverReady = true;
                finishNow();
                return false;
            }
            return true;
        }
        if (elapsed < MIN_DURATION_MS) return true;
        finishNow();
        return false;
    }

    public static boolean isReady() {
        return !active;
    }

    public static void cancel() {
        queue.clear();
        readyListeners.clear();
        active = false;
        serverReady = false;
        activeSceneId = "";
        beginTimeMs = 0L;
    }

    public static String sceneId() {
        return activeSceneId;
    }

    public static void setGameName(String name) {
        gameName = name == null ? "" : name;
    }

    public static String gameName() {
        return gameName;
    }

    public static float elapsedSeconds() {
        if (!active || beginTimeMs <= 0L) return 0.0f;
        return Math.max(0L, System.currentTimeMillis() - beginTimeMs) / 1000.0f;
    }

    public static void addReadyListener(Runnable listener) {
        if (listener == null) return;
        if (!active) {
            listener.run();
            return;
        }
        readyListeners.add(listener);
    }

    private static void maybeFinish() {
        if (!active) return;
        if (!serverReady) return;
        if (!queue.isEmpty()) return;
        long elapsed = System.currentTimeMillis() - beginTimeMs;
        if (elapsed < MIN_DURATION_MS) return;
        finishNow();
    }

    private static void finishNow() {
        if (!active) return;
        active = false;
        List<Runnable> toRun = new ArrayList<>(readyListeners);
        readyListeners.clear();
        for (Runnable r : toRun) {
            try {
                r.run();
            } catch (Throwable t) {
                ClientDebugLog.error("PlayLoading", "ready listener threw", t);
            }
        }
    }
}
