package com.meekdev.moud.core.math;

// linear, not srgb
public record Color(float r, float g, float b, float a) {

    public static final Color WHITE = new Color(1, 1, 1, 1);
    public static final Color BLACK = new Color(0, 0, 0, 1);
    public static final Color CLEAR = new Color(0, 0, 0, 0);

    public Color(float r, float g, float b) {
        this(r, g, b, 1f);
    }

    public static Color rgb(int packed) {
        return new Color(((packed >> 16) & 0xFF) / 255f, ((packed >> 8) & 0xFF) / 255f, (packed & 0xFF) / 255f, 1f);
    }

    public Color withAlpha(float alpha) {
        return new Color(r, g, b, alpha);
    }

    public Color lerp(Color o, float t) {
        return new Color(r + (o.r - r) * t, g + (o.g - g) * t, b + (o.b - b) * t, a + (o.a - a) * t);
    }
}
