package com.meekdev.moud.mod.adapter.ui;

import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.mojang.blaze3d.opengl.GlTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.network.chat.FontDescription;

final class GameText {

    static final float LINE = 9f;

    private GameText() {}

    static float width(FontDescription font, String text, float size) {
        GlyphSource glyphs = Minecraft.getInstance().font.getGlyphSource(font);
        float scale = size / LINE;
        float pen = 0;
        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            pen += glyphs.getGlyph(codepoint).info().getAdvance();
            i += Character.charCount(codepoint);
        }
        return pen * scale;
    }

    static void draw(UiDraw d, FontDescription font, String text, float x, float y, float size, int argb,
                     boolean shadow) {
        GlyphSource glyphs = Minecraft.getInstance().font.getGlyphSource(font);
        float scale = size / LINE;
        if (shadow) {
            int dark = (argb & 0xFF000000) | ((argb & 0xFCFCFC) >> 2);
            run(d, glyphs, text, x + scale, y + scale, scale, dark);
        }
        run(d, glyphs, text, x, y, scale, argb);
    }

    private static void run(UiDraw d, GlyphSource glyphs, String text, float x, float y, float scale, int argb) {
        float pen = x;
        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            BakedGlyph glyph = glyphs.getGlyph(codepoint);
            if (glyph instanceof BakedSheetGlyph sheet && sheet.textureView.texture() instanceof GlTexture gl) {
                d.imageRegion(gl.glId(),
                        pen + sheet.left * scale, y + sheet.up * scale,
                        pen + sheet.right * scale, y + sheet.down * scale,
                        sheet.u0, sheet.v0, sheet.u1, sheet.v1, argb, true);
            }
            pen += glyph.info().getAdvance() * scale;
            i += Character.charCount(codepoint);
        }
    }
}
