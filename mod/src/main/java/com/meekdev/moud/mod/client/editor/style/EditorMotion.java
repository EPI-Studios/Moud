package com.meekdev.moud.mod.client.editor.style;

import imgui.ImGui;

import java.util.HashMap;
import java.util.Map;

public final class EditorMotion {

    public static final float DEFAULT_DURATION_SECONDS = 0.11f;
    private static final float SETTLE_THRESHOLD = 0.002f;
    private static final float MINIMUM_DURATION_SECONDS = 0.0001f;
    private static final int IDLE_FRAME_LIMIT = 120;
    private static final int SWEEP_SIZE_THRESHOLD = 256;
    private static final Map<Integer, Track> TRACKS = new HashMap<>();

    private EditorMotion() {
    }

    public static float valueOf(String id) {
        int key = id.hashCode();
        return TRACKS.containsKey(key) ? TRACKS.get(key).value : 0.0f;
    }

    public static float towards(String id, boolean active) {
        return towards(id, active ? 1.0f : 0.0f, DEFAULT_DURATION_SECONDS);
    }

    public static float towards(String id, float target, float durationSeconds) {
        Track track = TRACKS.computeIfAbsent(id.hashCode(), ignored -> new Track());
        track.value = stepped(track.value, target, durationSeconds);
        track.frame = ImGui.getFrameCount();
        sweepWhenCrowded();
        return track.value;
    }

    public static void forget(String id) {
        TRACKS.remove(id.hashCode());
    }

    public static int blend(int fromColorAbgr, int toColorAbgr, float amount) {
        float clamped = Math.clamp(amount, 0.0f, 1.0f);
        return EditorStyle.rgba(
                mixed(fromColorAbgr, toColorAbgr, 0, clamped),
                mixed(fromColorAbgr, toColorAbgr, 8, clamped),
                mixed(fromColorAbgr, toColorAbgr, 16, clamped),
                mixed(fromColorAbgr, toColorAbgr, 24, clamped));
    }

    private static float stepped(float value, float target, float durationSeconds) {
        float elapsed = ImGui.getIO().getDeltaTime();
        float duration = Math.max(durationSeconds, MINIMUM_DURATION_SECONDS);
        float rate = 1.0f - (float) Math.exp(-elapsed / duration);
        float advanced = value + (target - value) * rate;
        return Math.abs(target - advanced) < SETTLE_THRESHOLD ? target : advanced;
    }

    private static int mixed(int fromColorAbgr, int toColorAbgr, int shift, float amount) {
        int start = (fromColorAbgr >> shift) & 0xFF;
        int end = (toColorAbgr >> shift) & 0xFF;
        return Math.round(start + (end - start) * amount);
    }

    private static void sweepWhenCrowded() {
        if (TRACKS.size() < SWEEP_SIZE_THRESHOLD) {
            return;
        }
        int frame = ImGui.getFrameCount();
        TRACKS.values().removeIf(track -> frame - track.frame > IDLE_FRAME_LIMIT);
    }

    private static final class Track {

        private float value;
        private int frame;
    }
}
