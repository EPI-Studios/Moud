package com.moud.client.fabric.util;

public final class ParseUtils {
    private ParseUtils() {
    }

    public static float parseFloat(String raw, float fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            float v = Float.parseFloat(raw.trim());
            return Float.isFinite(v) ? v : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static boolean parseBool(String raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        String s = raw.trim().toLowerCase();
        if (s.isEmpty()) {
            return fallback;
        }
        return switch (s) {
            case "1", "true", "t", "yes", "y", "on" -> true;
            case "0", "false", "f", "no", "n", "off" -> false;
            default -> fallback;
        };
    }

    public static String trimFloat(float value) {
        if (!Float.isFinite(value)) {
            return "0";
        }
        if (Math.abs(value - Math.round(value)) < 1e-6f) {
            return Integer.toString(Math.round(value));
        }
        String s = Float.toString(value);
        if (s.indexOf('.') < 0) {
            return s;
        }
        while (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? "0" : s;
    }
}
