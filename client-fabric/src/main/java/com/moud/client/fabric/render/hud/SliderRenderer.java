package com.moud.client.fabric.render.hud;

import com.moud.net.protocol.SceneSnapshot;

public final class SliderRenderer implements ControlRenderer {

    public static final SliderRenderer HORIZONTAL = new SliderRenderer(false);
    public static final SliderRenderer VERTICAL   = new SliderRenderer(true);

    private final boolean vertical;

    private SliderRenderer(boolean vertical) {
        this.vertical = vertical;
    }

    @Override
    public void render(ControlRenderContext ctx, SceneSnapshot.NodeSnapshot node, int x, int y, int w, int h) {
        float value  = ControlRenderContext.floatProp(node, "value",      50f);
        float minVal = ControlRenderContext.floatProp(node, "min_value",   0f);
        float maxVal = ControlRenderContext.floatProp(node, "max_value",  100f);
        float frac   = ControlRenderContext.valueFraction(value, minVal, maxVal);

        if (vertical) {
            renderVertical(ctx, x, y, w, h, frac);
        } else {
            renderHorizontal(ctx, x, y, w, h, frac);
        }
    }

    private static void renderHorizontal(ControlRenderContext ctx, int x, int y, int w, int h, float frac) {
        int trackY = y + h / 2 - 2;
        ctx.fill(x, trackY, w, 4, ControlRenderContext.TRACK);
        int thumbX = x + (int)(frac * (w - 8));
        ctx.fill(thumbX, y + 2, 8, h - 4, ControlRenderContext.THUMB);
    }

    private static void renderVertical(ControlRenderContext ctx, int x, int y, int w, int h, float frac) {
        int trackX = x + w / 2 - 2;
        ctx.fill(trackX, y, 4, h, ControlRenderContext.TRACK);
        int thumbY = y + (int)((1f - frac) * (h - 8));
        ctx.fill(x + 2, thumbY, w - 4, 8, ControlRenderContext.THUMB);
    }
}
