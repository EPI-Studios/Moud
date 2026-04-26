package com.moud.client.fabric.render;

public enum PostProcessStage {
    WORLD,
    SCREEN;

    public static PostProcessStage parse(String s, PostProcessStage fallback) {
        if (s == null) return fallback;
        String t = s.trim().toLowerCase();
        return switch (t) {
            case "screen", "ui", "hud", "post-hud", "post_hud" -> SCREEN;
            case "world", "scene", "pre-hud", "pre_hud", ""   -> WORLD;
            default -> fallback;
        };
    }
}
