package com.moud.client.fabric.render.hud;

import com.moud.net.protocol.SceneSnapshot;

public final class CheckBoxRenderer implements ControlRenderer {

    public static final CheckBoxRenderer INSTANCE = new CheckBoxRenderer();

    private CheckBoxRenderer() {}

    @Override
    public void render(ControlRenderContext ctx, SceneSnapshot.NodeSnapshot node, int x, int y, int w, int h) {
        String text   = ControlRenderContext.strProp(node, "text", "");
        boolean checked = ControlRenderContext.boolProp(node, "checked", false);

        int boxSize = Math.min(h - 4, 12);
        int bx = x + 2;
        int by = y + (h - boxSize) / 2;

        ctx.fill(bx, by, boxSize, boxSize, ControlRenderContext.BG);
        ctx.border(bx, by, boxSize, boxSize, ControlRenderContext.BORDER);
        if (checked) {
            ctx.fill(bx + 2, by + 2, boxSize - 4, boxSize - 4, ControlRenderContext.CHECK);
        }

        ctx.text(text, bx + boxSize + 4, y + (h - ctx.fontHeight()) / 2, ControlRenderContext.TEXT, false);
    }
}
