package com.moud.client.fabric.render.hud;

import com.moud.net.protocol.SceneSnapshot;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

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

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static String prop(SceneSnapshot.NodeSnapshot node, String key) {
        List<SceneSnapshot.Property> props = node.properties();
        if (props == null) return null;
        for (SceneSnapshot.Property p : props) {
            if (p != null && key.equals(p.key())) return p.value();
        }
        return null;
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
