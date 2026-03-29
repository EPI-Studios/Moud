package com.moud.client.fabric.render.hud;

import com.moud.net.protocol.SceneSnapshot;

public final class ProgressBarRenderer implements ControlRenderer {

    public static final ProgressBarRenderer INSTANCE = new ProgressBarRenderer();

    private ProgressBarRenderer() {}

    @Override
    public void render(ControlRenderContext ctx, SceneSnapshot.NodeSnapshot node, int x, int y, int w, int h) {
        float value  = ControlRenderContext.floatProp(node, "value",     50f);
        float minVal = ControlRenderContext.floatProp(node, "min_value",  0f);
        float maxVal = ControlRenderContext.floatProp(node, "max_value", 100f);
        float frac   = ControlRenderContext.valueFraction(value, minVal, maxVal);

        float fr = ControlRenderContext.floatProp(node, "fill_color_r", 0.2f);
        float fg = ControlRenderContext.floatProp(node, "fill_color_g", 0.6f);
        float fb = ControlRenderContext.floatProp(node, "fill_color_b", 1.0f);
        float fa = ControlRenderContext.floatProp(node, "fill_color_a", 1.0f);

        ctx.fill(x, y, w, h, ControlRenderContext.TRACK);
        ctx.fill(x, y, Math.max(1, (int)(w * frac)), h, ControlRenderContext.argb(fr, fg, fb, fa));
        ctx.border(x, y, w, h, ControlRenderContext.BORDER);

        if (ControlRenderContext.boolProp(node, "show_percentage", true)) {
            String label = (int)(frac * 100) + "%";
            ctx.textCentered(label, x, y, w, h, ControlRenderContext.TEXT, false);
        }
    }
}
