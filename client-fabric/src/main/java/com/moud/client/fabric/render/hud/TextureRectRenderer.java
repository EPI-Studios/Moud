package com.moud.client.fabric.render.hud;

import com.moud.net.protocol.SceneSnapshot;

public final class TextureRectRenderer implements ControlRenderer {

    public static final TextureRectRenderer INSTANCE = new TextureRectRenderer();

    private TextureRectRenderer() {}

    @Override
    public void render(ControlRenderContext ctx, SceneSnapshot.NodeSnapshot node, int x, int y, int w, int h) {
        ctx.fill(x, y, w, h, ControlRenderContext.BG);
        ctx.border(x, y, w, h, ControlRenderContext.BORDER);
        ctx.hline(x, x + w - 1, y,         ControlRenderContext.BORDER);
        ctx.hline(x, x + w - 1, y + h - 1, ControlRenderContext.BORDER);
        ctx.vline(x,         y, y + h - 1,  ControlRenderContext.BORDER);
        ctx.vline(x + w - 1, y, y + h - 1,  ControlRenderContext.BORDER);
    }
}
