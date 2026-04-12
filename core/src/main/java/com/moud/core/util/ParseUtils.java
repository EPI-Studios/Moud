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

    public static double parseDouble(String v, double fallback) {
        if (v == null) {
            return fallback;
        }
        try {
            double d = Double.parseDouble(v.trim());
            return Double.isFinite(d) ? d : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static boolean parseBool(String v, boolean fallback) {
        if (v == null) {
            return fallback;
        }
        return switch (v.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "t", "yes", "y", "on"  -> true;
            case "false", "0", "f", "no", "n", "off" -> false;
            default -> fallback;
        };
    }

    public static boolean parseBool(String v) {
        return parseBool(v, false);
    }

    public static String alternatePropertyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (key.indexOf('_') >= 0) {
            StringBuilder out = new StringBuilder(key.length());
            boolean upperNext = false;
            for (int i = 0; i < key.length(); i++) {
                char c = key.charAt(i);
                if (c == '_') {
                    upperNext = true;
                    continue;
                }
                out.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            }
            return out.toString();
        }
        StringBuilder out = new StringBuilder(key.length() + 4);
        boolean hasUpper = false;
        for (int i = 0; i < key.length(); i++) {
            if (Character.isUpperCase(key.charAt(i))) { hasUpper = true; break; }
        }
        if (!hasUpper) {
            return null;
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isUpperCase(c)) {
                out.append('_').append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
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
            return "0";
        }
        if (Math.abs(v - Math.round(v)) < 1e-6f) {
            return Integer.toString(Math.round(v));
        }
        String s = Float.toString(v);
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
