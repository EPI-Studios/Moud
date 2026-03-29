package com.moud.client.fabric.render.hud;

import com.moud.net.protocol.SceneSnapshot;

public final class ColorRectRenderer implements ControlRenderer {

    public static final ColorRectRenderer INSTANCE = new ColorRectRenderer();

    private ColorRectRenderer() {}

    @Override
    public void render(ControlRenderContext ctx, SceneSnapshot.NodeSnapshot node, int x, int y, int w, int h) {
        float r = ControlRenderContext.floatProp(node, "color_r", 1f);
        float g = ControlRenderContext.floatProp(node, "color_g", 0f);
        float b = ControlRenderContext.floatProp(node, "color_b", 0f);
        float a = ControlRenderContext.floatProp(node, "color_a", 1f);
        ctx.fill(x, y, w, h, ControlRenderContext.argb(r, g, b, a));
    }
}
