package com.moud.client.fabric.render.hud;

import com.moud.net.protocol.SceneSnapshot;

public final class LabelRenderer implements ControlRenderer {

    public static final LabelRenderer INSTANCE = new LabelRenderer();
    private static final float DEFAULT_FONT_SIZE = 9f;

    private LabelRenderer() {}

    @Override
    public void render(ControlRenderContext ctx, SceneSnapshot.NodeSnapshot node, int x, int y, int w, int h) {
        String text = ControlRenderContext.strProp(node, "text", "");
        float fontSize = ControlRenderContext.floatProp(node, "font_size", DEFAULT_FONT_SIZE);
        float scale = Math.max(0.25f, fontSize / DEFAULT_FONT_SIZE);

        ctx.fill(x, y, w, h, ControlRenderContext.BG);
        ctx.border(x, y, w, h, ControlRenderContext.BORDER);
        if (!text.isEmpty()) {
            ctx.textScaledCentered(text, x + 3, y, w - 6, h, ControlRenderContext.TEXT, false, scale);
        }
    }
}
