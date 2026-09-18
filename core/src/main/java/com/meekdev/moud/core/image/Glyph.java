package com.meekdev.moud.core.image;

public record Glyph(int width, int height, int[] argb, double scale, double ascent, double advance) {

    public Glyph {
        if (width < 0 || height < 0 || argb.length != width * height) {
            throw new IllegalArgumentException("a glyph of " + width + " by " + height + " has " + argb.length + " pixels");
        }
    }

    public static Glyph space(double advance) {
        return new Glyph(0, 0, new int[0], 1, 0, advance);
    }

    public int texel(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0;
        return argb[y * width + x];
    }

    public boolean blank() {
        return width == 0 || height == 0;
    }
}
