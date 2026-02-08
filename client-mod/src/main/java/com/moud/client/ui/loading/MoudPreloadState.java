package com.moud.client.ui.loading;

public final class MoudPreloadState {
    private static volatile boolean active;
    private static volatile String phase = "Preparing...";
    private static volatile float progress;

    private MoudPreloadState() {
    }

    public static void begin() {
        active = true;
        phase = "Preparing...";
        progress = 0f;
    }

    public static void setPhase(String newPhase) {
        if (newPhase == null || newPhase.isBlank()) {
            return;
        }
        phase = newPhase;
    }

    public static void setProgress(float newProgress) {
        progress = Math.max(0f, Math.min(1f, newProgress));
    }

    public static void finish() {
        active = false;
        progress = 1f;
        phase = "Done";
    }

    public static void reset() {
        active = false;
        phase = "Preparing...";
        progress = 0f;
    }

    public static boolean isActive() {
        return active;
    }

    public static String phase() {
        return phase;
    }

    public static float progress() {
        return progress;
    }
}
