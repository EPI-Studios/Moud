package com.moud.client.fabric.render.hud;

import com.moud.net.protocol.SceneSnapshot;

import java.util.ArrayList;
import java.util.List;

public final class LabelRenderer implements ControlRenderer {

    public static final LabelRenderer INSTANCE = new LabelRenderer();
    private static final float DEFAULT_FONT_SIZE = 9f;
    private static final int LINE_SPACING = 2;
    private static final int PAD_X = 3;
    private static final int PAD_Y = 2;

    private LabelRenderer() {}

    @Override
    public void render(ControlRenderContext ctx, SceneSnapshot.NodeSnapshot node, int x, int y, int w, int h) {
        String text = ControlRenderContext.strProp(node, "text", "");
        if (text.isEmpty()) return;

        float fontSize = ControlRenderContext.floatProp(node, "font_size", DEFAULT_FONT_SIZE);
        float scale = Math.max(0.25f, fontSize / DEFAULT_FONT_SIZE);
        int lineHeight = Math.max(1, Math.round(ctx.fontHeight() * scale) + LINE_SPACING);

        String hAlign = normalize(ControlRenderContext.strProp(node, "h_align", "left"));
        String vAlign = normalize(ControlRenderContext.strProp(node, "v_align", "top"));
        boolean autowrap = ControlRenderContext.boolProp(node, "autowrap", false);
        int color = parseColor(node);

        int innerW = Math.max(1, w - PAD_X * 2);
        int innerH = Math.max(1, h - PAD_Y * 2);

        List<String> lines = prepareLines(ctx, text, scale, innerW, autowrap);
        int totalH = lines.size() * lineHeight;

        int startY = switch (vAlign) {
            case "center", "middle" -> y + PAD_Y + Math.max(0, (innerH - totalH) / 2);
            case "bottom" -> y + h - PAD_Y - totalH;
            default -> y + PAD_Y;
        };

        int textY = startY;
        for (String line : lines) {
            if (textY + lineHeight > y + h + 1) break;
            if (!line.isEmpty()) {
                int lineW = Math.round(ctx.textWidth(line) * scale);
                int textX = switch (hAlign) {
                    case "center" -> x + PAD_X + Math.max(0, (innerW - lineW) / 2);
                    case "right" -> x + w - PAD_X - lineW;
                    default -> x + PAD_X;
                };
                ctx.textScaled(line, textX, textY, color, false, scale);
            }
            textY += lineHeight;
        }
    }

    private static List<String> prepareLines(ControlRenderContext ctx, String text, float scale, int maxPixelW, boolean autowrap) {
        List<String> out = new ArrayList<>();
        String[] hard = text.replace("\r", "").split("\n", -1);
        if (!autowrap || maxPixelW <= 0) {
            for (String ln : hard) out.add(ln);
            return out;
        }
        for (String paragraph : hard) {
            if (paragraph.isEmpty()) {
                out.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" ", -1)) {
                String candidate = current.length() == 0 ? word : current + " " + word;
                int candidateW = Math.round(ctx.textWidth(candidate) * scale);
                if (candidateW <= maxPixelW || current.length() == 0) {
                    if (current.length() == 0) current.append(word);
                    else current.append(' ').append(word);
                } else {
                    out.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                }
            }
            out.add(current.toString());
        }
        return out;
    }

    private static int parseColor(SceneSnapshot.NodeSnapshot node) {
        String r = ControlRenderContext.strProp(node, "color_r", null);
        String g = ControlRenderContext.strProp(node, "color_g", null);
        String b = ControlRenderContext.strProp(node, "color_b", null);
        String a = ControlRenderContext.strProp(node, "color_a", null);
        if (r == null && g == null && b == null && a == null) return ControlRenderContext.TEXT;
        float fr = ControlRenderContext.floatProp(node, "color_r", 1f);
        float fg = ControlRenderContext.floatProp(node, "color_g", 1f);
        float fb = ControlRenderContext.floatProp(node, "color_b", 1f);
        float fa = ControlRenderContext.floatProp(node, "color_a", 1f);
        return ControlRenderContext.argb(fr, fg, fb, fa);
    }

    private static String normalize(String v) {
        return v == null ? "" : v.trim().toLowerCase();
    }
}
