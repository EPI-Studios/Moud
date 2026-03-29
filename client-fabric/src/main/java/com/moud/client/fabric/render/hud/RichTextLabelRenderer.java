package com.moud.client.fabric.render.hud;

import com.moud.net.protocol.SceneSnapshot;

public final class RichTextLabelRenderer implements ControlRenderer {

    public static final RichTextLabelRenderer INSTANCE = new RichTextLabelRenderer();

    private RichTextLabelRenderer() {}

    @Override
    public void render(ControlRenderContext ctx, SceneSnapshot.NodeSnapshot node, int x, int y, int w, int h) {
        String raw  = ControlRenderContext.strProp(node, "text", "");
        boolean bbcode = ControlRenderContext.boolProp(node, "bbcode_enabled", false);

        ctx.fill(x, y, w, h, ControlRenderContext.BG);
        ctx.border(x, y, w, h, ControlRenderContext.BORDER);

        if (!raw.isEmpty()) {
            net.minecraft.text.Text text = bbcode
                    ? BbCodeParser.parse(raw)
                    : net.minecraft.text.Text.literal(raw);
            ctx.richText(text, x + 3, y + (h - ctx.fontHeight()) / 2, false);
        }
    }
}
