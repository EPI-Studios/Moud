package com.moud.client.fabric.render.hud;

import com.moud.net.protocol.SceneSnapshot;

public final class LabelRenderer implements ControlRenderer {

    public static final LabelRenderer INSTANCE = new LabelRenderer();
    private static final float DEFAULT_FONT_SIZE = 9f;
    private static final int LINE_SPACING = 2;

    private LabelRenderer() {}

    @Override
    public void render(ControlRenderContext ctx, SceneSnapshot.NodeSnapshot node, int x, int y, int w, int h) {
        String text = ControlRenderContext.strProp(node, "text", "");
        float fontSize = ControlRenderContext.floatProp(node, "font_size", DEFAULT_FONT_SIZE);
        float scale = Math.max(0.25f, fontSize / DEFAULT_FONT_SIZE);

        if (text.isEmpty()) {
            return;
        }

        String[] lines = text.replace("\r", "").split("\n", -1);
        int lineHeight = Math.max(1, Math.round(ctx.fontHeight() * scale) + LINE_SPACING);
        int textY = y + 2;

        for (String line : lines) {
            if (textY + lineHeight > y + h) {
                break;
            }
            if (!line.isEmpty()) {
                ctx.textScaled(line, x + 3, textY, ControlRenderContext.TEXT, false, scale);
            }
            textY += lineHeight;
        }
    }
}
