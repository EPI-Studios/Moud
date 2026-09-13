package com.meekdev.moud.mod.adapter.text;

import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.text.RichText;
import com.meekdev.moud.core.ui.HorizontalAlign;
import com.meekdev.moud.mod.adapter.chat.ChatShaders;
import com.meekdev.moud.mod.adapter.text.TextLayout.Glyph;
import com.meekdev.moud.mod.adapter.text.TextLayout.Kind;
import com.meekdev.moud.mod.adapter.text.TextLayout.Row;
import com.meekdev.moud.mod.adapter.ui.UiFonts;
import com.meekdev.moud.mod.adapter.ui.UiImages;
import com.mojang.blaze3d.opengl.GlTexture;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class TextPainter {

    public interface Hits {
        void glyph(Glyph glyph, float x, float y);

        void item(ItemStack stack, float x, float y, float size);
    }

    private static final float LINE = TextLayout.LINE;
    private static final long START = System.nanoTime();

    private TextPainter() {}

    public static float time() {
        return (float) ((System.nanoTime() - START) / 1e9);
    }

    public static void draw(UiDraw d, String markup, float x, float y, float width, float px, TextLook look, float alpha,
                            HorizontalAlign align) {
        TextLayout layout = TextLayout.of(d, markup, width, px, look.font());
        draw(d, layout, x, y, width, align, look, alpha, time(), Integer.MAX_VALUE, null);
    }

    public static void draw(UiDraw d, TextLayout layout, float x, float y, float width, HorizontalAlign align,
                            TextLook look, float alpha, float time, int reveal, Hits hits) {
        List<Runnable> shaded = new ArrayList<>();
        float rowTop = y;
        for (Row row : layout.rows()) {
            float rowX = x + row.offset(align, width);
            for (Glyph glyph : row.glyphs()) {
                if (glyph.index() >= reveal) break;
                glyph(d, look, glyph, rowX + glyph.x(), rowTop + row.height() - glyph.height(), alpha, time, shaded, hits);
            }
            rowTop += row.height();
        }
    }

    private static void glyph(UiDraw d, TextLook look, Glyph glyph, float x, float y, float alpha, float time,
                              List<Runnable> shaded, Hits hits) {
        RichText.Style style = glyph.style();
        if (hits != null && (style.click() != null || style.hover() != null || style.body() >= 0 || glyph.kind() == Kind.ITEM)) {
            hits.glyph(glyph, x, y);
        }
        float a = (float) (alpha * (1 - style.transparency()));
        float dx = 0;
        float dy = 0;
        float spin = 0;
        float px = glyph.height();
        for (RichText.Effect effect : style.effects()) {
            double strength = effect.number("strength", 1) * px / LINE;
            double speed = effect.number("speed", 1);
            int i = glyph.index() - glyph.spanStart();
            switch (effect.name()) {
                case "wave" -> dy += (float) (Math.sin(time * speed * 5 + i * 0.6) * strength);
                case "bounce" -> dy -= (float) (Math.abs(Math.sin(time * speed * 4 + i * 0.35)) * strength * 2);
                case "shake" -> {
                    long tick = (long) (time * 30 * speed);
                    dx += (float) ((hash(i, tick) - 0.5) * strength);
                    dy += (float) ((hash(i + 7919, tick) - 0.5) * strength);
                }
                case "pulse" -> a *= (float) (0.55 + 0.45 * Math.sin(time * speed * 4));
                case "fade" -> a *= (float) (0.5 + 0.5 * Math.sin(time * speed * 3 + i * 0.4));
                case "spin" -> spin += (float) (time * speed * 3);
                default -> {}
            }
        }
        if (a <= 0.003) return;
        x += dx;
        y += dy;

        if (style.mark() != null) {
            d.rect(x, y, glyph.width(), glyph.height(), Argb.of(style.mark(), style.mark().a() * a));
        }
        switch (glyph.kind()) {
            case IMAGE -> {
                int texture = UiImages.texture((String) glyph.extra());
                if (texture != 0) d.image(texture, x, y, glyph.width(), glyph.height(), Argb.of(Color.WHITE, a));
                return;
            }
            case ITEM -> {
                if (a > 0.5 && hits != null) hits.item((ItemStack) glyph.extra(), x, y, glyph.height());
                return;
            }
            default -> {}
        }

        Color base = style.color() != null ? style.color() : look.textColor();
        if (style.gradientTo() != null && glyph.spanLength() > 1) {
            float t = (glyph.index() - glyph.spanStart()) / (float) (glyph.spanLength() - 1);
            base = lerp(base, style.gradientTo(), t);
        }
        if (style.rainbow() > 0) {
            float hue = (float) ((glyph.index() * 0.06 + time * 0.25 * style.rainbow()) % 1.0);
            int rgb = hsb(hue, 0.7f, 1f);
            base = new Color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1);
        }
        int fill = Argb.of(base, a);
        int codepoint = glyph.codepoint();
        if (style.obfuscated() && codepoint != ' ') codepoint = 33 + (int) (hash(glyph.index(), (long) (time * 20)) * 93);

        UiFonts.Face face = UiFonts.of(style.font() != null ? style.font() : look.font());
        float scale = px / LINE;
        ShaderProgram shader = style.shader() == null ? null : ChatShaders.text(style.shader());

        Color strokeColor = style.stroke() != null ? style.stroke() : look.strokeColor();
        double strokeAlpha = style.stroke() != null ? style.stroke().a() : look.strokeAlpha();
        float thickness = (float) (style.stroke() != null ? style.strokeThickness() : 1) * scale;
        boolean shadow = style.shadow() != null ? style.shadow().a() > 0 : look.shadow();
        int shadowArgb = style.shadow() != null ? Argb.of(style.shadow(), a) : (fill & 0xFF000000) | ((fill & 0xFCFCFC) >> 2);

        if (face instanceof UiFonts.Vector vector) {
            Identifier previous = d.currentFont();
            d.font(vector.font());
            float baseline = y + px * 0.8f;
            if (shadow) d.glyph(codepoint, x + scale, baseline + scale, px, shadowArgb, 0, 0);
            if (strokeAlpha > 0.003) d.glyph(codepoint, x, baseline, px, Argb.of(strokeColor, strokeAlpha * a), -thickness, 0);
            d.glyph(codepoint, x, baseline, px, fill, 0, 0);
            if (style.bold()) d.glyph(codepoint, x + scale * 0.5f, baseline, px, fill, 0, 0);
            if (previous != null) d.font(previous);
        } else {
            FontDescription font = ((UiFonts.Game) face).font();
            BakedGlyph baked = TextLayout.source(font).getGlyph(codepoint);
            if (baked instanceof BakedSheetGlyph sheet && sheet.textureView.texture() instanceof GlTexture gl) {
                if (shadow) quad(d, gl.glId(), sheet, x + scale, y + scale, scale, style.italic(), spin, shadowArgb, null, shaded);
                if (strokeAlpha > 0.003) {
                    int strokeArgb = Argb.of(strokeColor, strokeAlpha * a);
                    for (int ox = -1; ox <= 1; ox++) {
                        for (int oy = -1; oy <= 1; oy++) {
                            if (ox == 0 && oy == 0) continue;
                            quad(d, gl.glId(), sheet, x + ox * thickness, y + oy * thickness, scale, style.italic(), spin, strokeArgb, null, shaded);
                        }
                    }
                }
                quad(d, gl.glId(), sheet, x, y, scale, style.italic(), spin, fill, shader, shaded);
                if (style.bold()) quad(d, gl.glId(), sheet, x + scale, y, scale, style.italic(), spin, fill, shader, shaded);
            }
        }
        if (style.underline()) d.rect(x, y + px - scale, glyph.width(), scale, fill);
        if (style.strike()) d.rect(x, y + px * 0.45f, glyph.width(), scale, fill);
        if (!shaded.isEmpty()) {
            List<Runnable> run = new ArrayList<>(shaded);
            shaded.clear();
            d.withProgram(shader, program -> program.setFloat("Time", time), () -> run.forEach(Runnable::run));
        }
    }

    private static void quad(UiDraw d, int texture, BakedSheetGlyph sheet, float x, float y, float scale, boolean italic,
                             float spin, int argb, ShaderProgram shader, List<Runnable> shaded) {
        float x0 = x + sheet.left * scale;
        float x1 = x + sheet.right * scale;
        float y0 = y + sheet.up * scale;
        float y1 = y + sheet.down * scale;
        float lean = italic ? (y1 - y0) * 0.25f : 0;
        float[] corners = {x0 + lean, y0, x1 + lean, y0, x1, y1, x0, y1};
        if (spin != 0) {
            float cx = (x0 + x1) / 2;
            float cy = (y0 + y1) / 2;
            float cos = (float) Math.cos(spin);
            float sin = (float) Math.sin(spin);
            for (int i = 0; i < 8; i += 2) {
                float px = corners[i] - cx;
                float py = corners[i + 1] - cy;
                corners[i] = cx + px * cos - py * sin;
                corners[i + 1] = cy + px * sin + py * cos;
            }
        }
        Runnable draw = () -> d.imageQuad(texture, corners[0], corners[1], corners[2], corners[3], corners[4], corners[5],
                corners[6], corners[7], sheet.u0, sheet.v0, sheet.u1, sheet.v1, argb, true);
        if (shader != null) {
            shaded.add(draw);
        } else {
            draw.run();
        }
    }

    private static double hash(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    private static int hsb(float hue, float saturation, float brightness) {
        float h = (hue - (float) Math.floor(hue)) * 6f;
        float c = brightness * saturation;
        float x = c * (1 - Math.abs(h % 2 - 1));
        float m = brightness - c;
        float r, g, b;
        switch ((int) h) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
    }

    private static Color lerp(Color a, Color b, float t) {
        return new Color(a.r() + (b.r() - a.r()) * t, a.g() + (b.g() - a.g()) * t, a.b() + (b.b() - a.b()) * t,
                a.a() + (b.a() - a.a()) * t);
    }
}
