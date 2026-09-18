package com.meekdev.moud.core.image;

import java.util.Map;

public final class GlyphFont {

    public static final double BASELINE = 7;
    public static final Glyph MISSING = box(5, 8);

    private final Map<Integer, Glyph> glyphs;
    private final double lineHeight;
    private final double ascent;
    private final double descent;

    public GlyphFont(Map<Integer, Glyph> glyphs, double lineHeight) {
        this.glyphs = Map.copyOf(glyphs);
        this.lineHeight = lineHeight;
        double up = MISSING.ascent();
        double down = MISSING.height() * MISSING.scale() - MISSING.ascent();
        for (Glyph glyph : this.glyphs.values()) {
            if (glyph.blank()) continue;
            up = Math.max(up, glyph.ascent());
            down = Math.max(down, glyph.height() * glyph.scale() - glyph.ascent());
        }
        this.ascent = up;
        this.descent = down;
    }

    private static Glyph box(int width, int height) {
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) argb[y * width + x] = 0xFFFFFFFF;
            }
        }
        return new Glyph(width, height, argb, 1, BASELINE, width + 1);
    }

    public Glyph glyph(int codePoint) {
        return glyphs.getOrDefault(codePoint, MISSING);
    }

    public boolean has(int codePoint) {
        return glyphs.containsKey(codePoint);
    }

    public int size() {
        return glyphs.size();
    }

    public double lineHeight() {
        return lineHeight;
    }

    public double ascent() {
        return ascent;
    }

    public double descent() {
        return descent;
    }
}
