package com.moud.client.fabric.render.hud;

import com.moud.client.fabric.render.scene.util.NodePropertyUtils;
import com.moud.net.protocol.SceneSnapshot;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ControlRenderContext {

    public static final int BG       = 0xCC1E1E2E;
    public static final int BG_DIM   = 0x441E1E2E;
    public static final int BG_GHOST = 0x221E1E2E;
    public static final int BORDER   = 0xFF555577;
    public static final int TEXT     = 0xFFEEEEFF;
    public static final int TEXT_DIM = 0xFF888899;
    public static final int ACCENT   = 0xFF4040A0;
    public static final int CHECK    = 0xFF66BB66;
    public static final int TRACK    = 0xFF333344;
    public static final int THUMB    = 0xFF8080CC;

    private final DrawContext drawContext;
    private final TextRenderer textRenderer;
    private final float scaleFactor;

    private float mR = 1f, mG = 1f, mB = 1f, mA = 1f;

    private final Deque<float[]> modulateStack = new ArrayDeque<>();

    public ControlRenderContext(DrawContext drawContext, TextRenderer textRenderer, float scaleFactor) {
        this.drawContext  = drawContext;
        this.textRenderer = textRenderer;
        this.scaleFactor  = Math.max(1f, scaleFactor);
    }

    public void saveModulate(float r, float g, float b, float a) {
        modulateStack.push(new float[]{mR, mG, mB, mA});
        mR = clamp01(mR * r);
        mG = clamp01(mG * g);
        mB = clamp01(mB * b);
        mA = clamp01(mA * a);
    }

    public void restoreModulate() {
        float[] prev = modulateStack.poll();
        if (prev != null) { mR = prev[0]; mG = prev[1]; mB = prev[2]; mA = prev[3]; }
    }

    public float modulateAlpha() { return mA; }

    public void fill(int x, int y, int w, int h, int argb) {
        drawContext.fill(x, y, x + w, y + h, mulAlpha(argb, mA));
    }

    public void border(int x, int y, int w, int h, int argb) {
        drawContext.drawBorder(x, y, w, h, mulAlpha(argb, mA));
    }

    public void hline(int x1, int x2, int y, int argb) {
        drawContext.drawHorizontalLine(x1, x2, y, mulAlpha(argb, mA));
    }

    public void vline(int x, int y1, int y2, int argb) {
        drawContext.drawVerticalLine(x, y1, y2, mulAlpha(argb, mA));
    }

    public void text(String s, int x, int y, int argb, boolean shadow) {
        if (s != null && !s.isEmpty())
            drawContext.drawText(textRenderer, s, x, y, mulAlpha(argb, mA), shadow);
    }

    public int textWidth(String s) {
        return s == null ? 0 : textRenderer.getWidth(s);
    }

    public int fontHeight() {
        return textRenderer.fontHeight;
    }

    public void textCentered(String s, int x, int y, int w, int h, int argb, boolean shadow) {
        if (s == null || s.isEmpty()) return;
        int tx = x + (w - textRenderer.getWidth(s)) / 2;
        int ty = y + (h - textRenderer.fontHeight) / 2;
        drawContext.drawText(textRenderer, s, tx, ty, mulAlpha(argb, mA), shadow);
    }

    public void textScaled(String s, int x, int y, int argb, boolean shadow, float scale) {
        if (s == null || s.isEmpty()) return;
        if (Math.abs(scale - 1f) < 0.01f) {
            text(s, x, y, argb, shadow);
            return;
        }
        drawContext.getMatrices().push();
        drawContext.getMatrices().scale(scale, scale, 1f);
        drawContext.drawText(textRenderer, s, (int)(x / scale), (int)(y / scale), mulAlpha(argb, mA), shadow);
        drawContext.getMatrices().pop();
    }

    public void textScaledCentered(String s, int x, int y, int w, int h, int argb, boolean shadow, float scale) {
        if (s == null || s.isEmpty()) return;
        int tw = (int)(textRenderer.getWidth(s) * scale);
        int fh = (int)(textRenderer.fontHeight * scale);
        textScaled(s, x + (w - tw) / 2, y + (h - fh) / 2, argb, shadow, scale);
    }

    public void richText(Text text, int x, int y, boolean shadow) {
        if (text == null) return;
        drawContext.drawText(textRenderer, text, x, y, mulAlpha(TEXT, mA), shadow);
    }

    public void enableScissor(int x, int y, int w, int h) {
        int x1 = (int)(x / scaleFactor);
        int y1 = (int)(y / scaleFactor);
        int x2 = (int)((x + w) / scaleFactor);
        int y2 = (int)((y + h) / scaleFactor);
        drawContext.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        drawContext.disableScissor();
    }

    public void drawTexture(Identifier textureId,
                            int x, int y, int w, int h,
                            float u0, float v0, float u1, float v1,
                            int textureWidth, int textureHeight,
                            int argb) {
        if (textureId == null || w <= 0 || h <= 0 || textureWidth <= 0 || textureHeight <= 0) {
            return;
        }
        int tinted = mulColor(argb, mR, mG, mB, mA);
        drawContext.setShaderColor(
                ((tinted >> 16) & 0xFF) / 255.0f,
                ((tinted >> 8) & 0xFF) / 255.0f,
                (tinted & 0xFF) / 255.0f,
                ((tinted >> 24) & 0xFF) / 255.0f
        );
        drawContext.drawTexture(
                textureId,
                x, y, w, h,
                u0 * textureWidth,
                v0 * textureHeight,
                Math.max(1, Math.round((u1 - u0) * textureWidth)),
                Math.max(1, Math.round((v1 - v0) * textureHeight)),
                textureWidth,
                textureHeight
        );
        drawContext.setShaderColor(1f, 1f, 1f, 1f);
    }

    public void drawNinePatch(Identifier textureId,
                              int x, int y, int w, int h,
                              int texW, int texH,
                              int left, int top, int right, int bottom,
                              int argb) {
        if (textureId == null || w <= 0 || h <= 0 || texW <= 0 || texH <= 0) return;

        int tinted = mulColor(argb, mR, mG, mB, mA);
        drawContext.setShaderColor(
                ((tinted >> 16) & 0xFF) / 255.0f,
                ((tinted >> 8) & 0xFF) / 255.0f,
                (tinted & 0xFF) / 255.0f,
                ((tinted >> 24) & 0xFF) / 255.0f
        );

        int centerW = Math.max(0, w - left - right);
        int centerH = Math.max(0, h - top - bottom);
        int texCenterW = Math.max(0, texW - left - right);
        int texCenterH = Math.max(0, texH - top - bottom);

        if (top > 0) {
            if (left > 0) drawContext.drawTexture(textureId, x, y, left, top, 0, 0, left, top, texW, texH);
            if (centerW > 0 && texCenterW > 0) drawContext.drawTexture(textureId, x + left, y, centerW, top, left, 0, texCenterW, top, texW, texH);
            if (right > 0) drawContext.drawTexture(textureId, x + left + centerW, y, right, top, texW - right, 0, right, top, texW, texH);
        }
        if (centerH > 0 && texCenterH > 0) {
            if (left > 0) drawContext.drawTexture(textureId, x, y + top, left, centerH, 0, top, left, texCenterH, texW, texH);
            if (centerW > 0 && texCenterW > 0) drawContext.drawTexture(textureId, x + left, y + top, centerW, centerH, left, top, texCenterW, texCenterH, texW, texH);
            if (right > 0) drawContext.drawTexture(textureId, x + left + centerW, y + top, right, centerH, texW - right, top, right, texCenterH, texW, texH);
        }
        if (bottom > 0) {
            if (left > 0) drawContext.drawTexture(textureId, x, y + top + centerH, left, bottom, 0, texH - bottom, left, bottom, texW, texH);
            if (centerW > 0 && texCenterW > 0) drawContext.drawTexture(textureId, x + left, y + top + centerH, centerW, bottom, left, texH - bottom, texCenterW, bottom, texW, texH);
            if (right > 0) drawContext.drawTexture(textureId, x + left + centerW, y + top + centerH, right, bottom, texW - right, texH - bottom, right, bottom, texW, texH);
        }

        drawContext.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static int argb(float r, float g, float b, float a) {
        return (Math.round(clamp01(a) * 255f) << 24)
             | (Math.round(clamp01(r) * 255f) << 16)
             | (Math.round(clamp01(g) * 255f) << 8)
             |  Math.round(clamp01(b) * 255f);
    }

    public static int mulAlpha(int argb, float factor) {
        int a = (int)(((argb >>> 24) & 0xFF) * clamp01(factor));
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    public static int mulColor(int argb, float rFactor, float gFactor, float bFactor, float aFactor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * clamp01(aFactor));
        int r = Math.round(((argb >>> 16) & 0xFF) * clamp01(rFactor));
        int g = Math.round(((argb >>> 8) & 0xFF) * clamp01(gFactor));
        int b = Math.round((argb & 0xFF) * clamp01(bFactor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static String prop(SceneSnapshot.NodeSnapshot node, String key) {
        return NodePropertyUtils.stringProp(node, key);
    }

    public static float floatProp(SceneSnapshot.NodeSnapshot node, String key, float def) {
        String v = prop(node, key);
        if (v == null || v.isBlank()) return def;
        try { return Float.parseFloat(v); } catch (NumberFormatException e) { return def; }
    }

    public static boolean boolProp(SceneSnapshot.NodeSnapshot node, String key, boolean def) {
        String v = prop(node, key);
        if (v == null) return def;
        return "true".equalsIgnoreCase(v.trim());
    }

    public static String strProp(SceneSnapshot.NodeSnapshot node, String key, String def) {
        String v = prop(node, key);
        return v != null ? v : def;
    }

    public static float valueFraction(float value, float min, float max) {
        return max > min ? clamp01((value - min) / (max - min)) : 0f;
    }
}
