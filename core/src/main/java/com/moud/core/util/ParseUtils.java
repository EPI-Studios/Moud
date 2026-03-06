package com.moud.core.util;


import java.util.Locale;

public final class ParseUtils {
    private ParseUtils() {
    }

    public static float parseFloat(String v, float fallback) {
        if (v == null) {
            return fallback;
        }
        try {
            float f = Float.parseFloat(v.trim());
            return Float.isFinite(f) ? f : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static int parseInt(String v, int fallback) {
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static boolean parseBool(String v, boolean fallback) {
        if (v == null) {
            return fallback;
        }
        String s = v.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(s) || "1".equals(s)) {
            return true;
        }
        if ("false".equals(s) || "0".equals(s)) {
            return false;
        }
        return fallback;
    }

    public static boolean parseBool(String v) {
        return parseBool(v, false);
    }

    public static float finiteOr(float v, float fallback) {
        return Float.isFinite(v) ? v : fallback;
    }

    public static String defaulted(String v, String fallback) {
        if (v == null || v.isBlank()) {
            return fallback;
        }
        return v;
    }

    public static String trimFloat(float v) {
        if (!Float.isFinite(v)) {
            v = 0.0f;
        }
        if (Math.abs(v - Math.round(v)) < 1e-6f) {
            return Integer.toString((int) Math.round(v));
        }
        return Float.toString(v);
    }

    public static float[] parseLegacyRgb(String csv, float[] fallback) {
        float r = fallback.length > 0 ? fallback[0] : 0.5f;
        float g = fallback.length > 1 ? fallback[1] : 0.5f;
        float b = fallback.length > 2 ? fallback[2] : 0.5f;
        if (csv == null || csv.isBlank()) {
            return new float[]{r, g, b};
        }
        int c1 = csv.indexOf(',');
        int c2 = c1 < 0 ? -1 : csv.indexOf(',', c1 + 1);
        if (c1 > 0 && c2 > c1) {
            r = parseFloat(csv.substring(0, c1), r);
            g = parseFloat(csv.substring(c1 + 1, c2), g);
            b = parseFloat(csv.substring(c2 + 1), b);
        }
        return new float[]{r, g, b};
    }
}
