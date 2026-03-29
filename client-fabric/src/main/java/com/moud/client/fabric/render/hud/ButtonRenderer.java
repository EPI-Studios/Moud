package com.moud.client.fabric.render.hud;

import com.moud.net.protocol.SceneSnapshot;

public final class ButtonRenderer implements ControlRenderer {

    public static final ButtonRenderer INSTANCE = new ButtonRenderer();

    private ButtonRenderer() {}

    @Override
    public void render(ControlRenderContext ctx, SceneSnapshot.NodeSnapshot node, int x, int y, int w, int h) {
        String text     = ControlRenderContext.strProp(node, "text", "");
        boolean disabled = ControlRenderContext.boolProp(node, "disabled", false);
        int bg = disabled ? ControlRenderContext.BG : ControlRenderContext.ACCENT;
        ctx.fill(x, y, w, h, bg);
        ctx.border(x, y, w, h, ControlRenderContext.BORDER);
        ctx.textCentered(text, x, y, w, h, ControlRenderContext.TEXT, true);
    }
}
