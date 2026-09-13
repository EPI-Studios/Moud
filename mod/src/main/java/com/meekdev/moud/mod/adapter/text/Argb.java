package com.meekdev.moud.mod.adapter.text;

import com.meekdev.moud.core.math.Color;

public final class Argb {

    private Argb() {}

    public static int of(Color c, double alpha) {
        int a = (int) Math.round(Math.clamp(c.a() * alpha, 0, 1) * 255);
        return a << 24 | rgb(new Color(Math.clamp(c.r(), 0f, 1f), Math.clamp(c.g(), 0f, 1f), Math.clamp(c.b(), 0f, 1f), 1));
    }

    public static int rgb(Color color) {
        return (Math.round(color.r() * 255) << 16) | (Math.round(color.g() * 255) << 8) | Math.round(color.b() * 255);
    }

    public static String hex(Color color) {
        return String.format("#%06x", rgb(color));
    }
}
