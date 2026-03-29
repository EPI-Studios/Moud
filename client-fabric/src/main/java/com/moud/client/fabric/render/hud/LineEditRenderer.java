package com.moud.client.fabric.render.hud;

import com.moud.net.protocol.SceneSnapshot;

public final class LineEditRenderer implements ControlRenderer {

    public static final LineEditRenderer INSTANCE = new LineEditRenderer();

    private LineEditRenderer() {}

    @Override
    public void render(ControlRenderContext ctx, SceneSnapshot.NodeSnapshot node, int x, int y, int w, int h) {
        String text        = ControlRenderContext.strProp(node, "text", "");
        String placeholder = ControlRenderContext.strProp(node, "placeholder", "");

        ctx.fill(x, y, w, h, ControlRenderContext.BG);
        ctx.border(x, y, w, h, ControlRenderContext.BORDER);

        if (!text.isEmpty()) {
            ctx.text(text, x + 3, y + (h - ctx.fontHeight()) / 2, ControlRenderContext.TEXT, false);
        } else if (!placeholder.isEmpty()) {
            ctx.text(placeholder, x + 3, y + (h - ctx.fontHeight()) / 2, ControlRenderContext.TEXT_DIM, false);
        }

        int cursorX = x + 3 + ctx.textWidth(text);
        ctx.vline(cursorX, y + 3, y + h - 4, ControlRenderContext.TEXT);
    }
}
