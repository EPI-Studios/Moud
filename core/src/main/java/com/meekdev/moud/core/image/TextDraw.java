package com.meekdev.moud.core.image;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TextDraw {

    private static final float SHADOW = 0.25f;
    private static final double ITALIC = 0.25;
    private static final double EPSILON = 1e-9;

    public enum Align {
        LEFT,
        CENTER,
        RIGHT
    }

    public record Style(double size, boolean shadow, Align align, double wrap, double lineHeight, boolean bold, boolean italic, boolean smooth) {

        public static final Style PLAIN = new Style(1, false, Align.LEFT, 0, 0, false, false, false);

        public Style {
            if (!(size > 0) || !Double.isFinite(size)) throw new IllegalArgumentException("text size must be above 0, not " + size);
        }
    }

    public record Extent(double width, double height) {}

    private record Line(int[] codePoints, double width) {}

    private TextDraw() {}

    public static Extent measure(GlyphFont font, String text, Style style) {
        return extent(font, lines(font, text, style), style);
    }

    public static Extent draw(EditableImage image, GlyphFont font, String text, double x, double y, Paint paint, Style style) {
        List<Line> lines = lines(font, text, style);
        double step = lineStep(font, style);
        double size = style.size();
        if (style.shadow()) {
            Paint shade = new Paint(paint.r() * SHADOW, paint.g() * SHADOW, paint.b() * SHADOW, paint.a(), paint.blend());
            write(image, font, lines, x + size, y + size, step, shade, style);
        }
        write(image, font, lines, x, y, step, paint, style);
        image.touched();
        return extent(font, lines, style);
    }

    private static void write(EditableImage image, GlyphFont font, List<Line> lines, double x, double y, double step, Paint paint, Style style) {
        double size = style.size();
        for (int n = 0; n < lines.size(); n++) {
            Line line = lines.get(n);
            double pen = switch (style.align()) {
                case LEFT -> x;
                case CENTER -> x - line.width() / 2;
                case RIGHT -> x - line.width();
            };
            double top = y + n * step;
            double origin = top + (font.ascent() - GlyphFont.BASELINE) * size;
            for (int codePoint : line.codePoints()) {
                Glyph glyph = font.glyph(codePoint);
                if (!glyph.blank()) {
                    double gy = top + (font.ascent() - glyph.ascent()) * size;
                    stamp(image, glyph, pen, gy, origin, paint, style);
                    if (style.bold()) stamp(image, glyph, pen + size, gy, origin, paint, style);
                }
                pen += advance(glyph, style);
            }
        }
    }

    private static void stamp(EditableImage image, Glyph glyph, double gx, double gy, double origin, Paint paint, Style style) {
        double size = style.size();
        double texel = glyph.scale() * size;
        double w = glyph.width() * texel;
        double h = glyph.height() * texel;
        double lean = style.italic() ? Math.max(Math.abs(slant(gy, origin, size)), Math.abs(slant(gy + h, origin, size))) : 0;
        double pad = style.smooth() ? texel : 0;
        int x0 = (int) Math.max(0, Math.floor(gx - lean - pad));
        int y0 = (int) Math.max(0, Math.floor(gy - pad));
        int x1 = (int) Math.min(image.width(), Math.ceil(gx + w + lean + pad));
        int y1 = (int) Math.min(image.height(), Math.ceil(gy + h + pad));
        int[] argb = image.pixels();
        for (int py = y0; py < y1; py++) {
            double v = (py + 0.5 - gy) / texel;
            double shift = style.italic() ? slant(py + 0.5, origin, size) : 0;
            for (int px = x0; px < x1; px++) {
                double u = (px + 0.5 - gx - shift) / texel;
                int sample;
                if (style.smooth()) {
                    sample = bilinear(glyph, u - 0.5, v - 0.5);
                } else {
                    if (u < 0 || v < 0 || u >= glyph.width() || v >= glyph.height()) continue;
                    sample = glyph.texel((int) u, (int) v);
                }
                float a = EditableImage.alpha(sample) * paint.a();
                if (a <= 0) continue;
                float r = ((sample >> 16) & 0xFF) / 255f;
                float g = ((sample >> 8) & 0xFF) / 255f;
                float b = (sample & 0xFF) / 255f;
                int at = py * image.width() + px;
                argb[at] = EditableImage.mix(argb[at], new Paint(r * paint.r(), g * paint.g(), b * paint.b(), a, paint.blend()), 1);
            }
        }
    }

    private static double slant(double at, double origin, double size) {
        double down = (at - origin) / size;
        return (1 - ITALIC * down) * size;
    }

    private static int bilinear(Glyph glyph, double u, double v) {
        int u0 = (int) Math.floor(u);
        int v0 = (int) Math.floor(v);
        double fu = u - u0;
        double fv = v - v0;
        int a = glyph.texel(u0, v0);
        int b = glyph.texel(u0 + 1, v0);
        int c = glyph.texel(u0, v0 + 1);
        int d = glyph.texel(u0 + 1, v0 + 1);
        double wa = (1 - fu) * (1 - fv) * EditableImage.alpha(a);
        double wb = fu * (1 - fv) * EditableImage.alpha(b);
        double wc = (1 - fu) * fv * EditableImage.alpha(c);
        double wd = fu * fv * EditableImage.alpha(d);
        double alpha = wa + wb + wc + wd;
        if (alpha <= 0) return 0;
        int out = (int) Math.round(Math.min(1, alpha) * 255) << 24;
        for (int shift = 0; shift <= 16; shift += 8) {
            double channel = (((a >> shift) & 0xFF) * wa + ((b >> shift) & 0xFF) * wb + ((c >> shift) & 0xFF) * wc + ((d >> shift) & 0xFF) * wd) / alpha;
            out |= ((int) Math.round(channel) & 0xFF) << shift;
        }
        return out;
    }

    private static double advance(Glyph glyph, Style style) {
        return (glyph.advance() + (style.bold() ? 1 : 0)) * style.size();
    }

    private static double lineStep(GlyphFont font, Style style) {
        return style.lineHeight() > 0 ? style.lineHeight() : font.lineHeight() * style.size();
    }

    private static Extent extent(GlyphFont font, List<Line> lines, Style style) {
        double widest = 0;
        for (Line line : lines) widest = Math.max(widest, line.width());
        double size = style.size();
        double shadow = style.shadow() ? size : 0;
        double height = (lines.size() - 1) * lineStep(font, style) + (font.ascent() + font.descent()) * size + shadow;
        return new Extent(widest > 0 ? widest + shadow : 0, height);
    }

    private static List<Line> lines(GlyphFont font, String text, Style style) {
        List<Line> out = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            int[] codePoints = paragraph.codePoints().filter(c -> c != '\r').toArray();
            if (style.wrap() > 0) wrap(font, codePoints, style, out);
            else out.add(line(font, codePoints, 0, codePoints.length, style));
        }
        return out;
    }

    private static void wrap(GlyphFont font, int[] codePoints, Style style, List<Line> out) {
        int n = codePoints.length;
        if (n == 0) {
            out.add(new Line(codePoints, 0));
            return;
        }
        int start = 0;
        while (start < n) {
            double width = 0;
            int end = start;
            int space = -1;
            while (end < n) {
                double step = advance(font.glyph(codePoints[end]), style);
                if (end > start && width + step > style.wrap() + EPSILON) break;
                if (codePoints[end] == ' ') space = end;
                width += step;
                end++;
            }
            if (end == n) {
                out.add(line(font, codePoints, start, n, style));
                return;
            }
            if (codePoints[end] == ' ') {
                out.add(line(font, codePoints, start, end, style));
                start = end;
            } else if (space > start) {
                out.add(line(font, codePoints, start, space, style));
                start = space;
            } else {
                out.add(line(font, codePoints, start, end, style));
                start = end;
            }
            while (start < n && codePoints[start] == ' ') start++;
        }
    }

    private static Line line(GlyphFont font, int[] codePoints, int from, int to, Style style) {
        int[] part = Arrays.copyOfRange(codePoints, from, to);
        double width = 0;
        for (int codePoint : part) width += advance(font.glyph(codePoint), style);
        return new Line(part, width);
    }
}
