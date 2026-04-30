package com.moud.core.tween;

public enum TweenLoopMode {
    ONCE,
    LOOP,
    PING_PONG;

    public static TweenLoopMode parse(String raw, TweenLoopMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase()) {
            case "loop", "repeat" -> LOOP;
            case "ping_pong", "pingpong", "yoyo" -> PING_PONG;
            case "once", "oneshot" -> ONCE;
            default -> fallback;
        };
    }
}
