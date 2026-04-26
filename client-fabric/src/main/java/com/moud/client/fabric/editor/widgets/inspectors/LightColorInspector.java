package com.moud.client.fabric.editor.widgets.inspectors;

import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.ColorPicker;
import com.moud.client.fabric.editor.widgets.InspectorContext;
import com.moud.client.fabric.editor.widgets.NodeInspectorWidget;
import com.moud.core.PropertyDef;
import com.moud.core.util.ParseUtils;

public final class LightColorInspector implements NodeInspectorWidget {
    private final ColorPicker picker = new ColorPicker();
    private boolean pickerOpen;
    private long lastNodeId;

    @Override
    public boolean handles(String propertyKey) {
        return "color_r".equals(propertyKey) || "color_g".equals(propertyKey) || "color_b".equals(propertyKey);
    }

    @Override
    public int renderRow(InspectorContext ctx, PropertyDef property, String value,
                         int x, int y, int width, int rowHeight, int labelWidth) {
        if (!"color_r".equals(property.key())) {
            return y;
        }

        float r = clamp01(ParseUtils.parseFloat(ctx.values().get("color_r"), 1f));
        float g = clamp01(ParseUtils.parseFloat(ctx.values().get("color_g"), 1f));
        float b = clamp01(ParseUtils.parseFloat(ctx.values().get("color_b"), 1f));

        if (ctx.nodeId() != lastNodeId) {
            lastNodeId = ctx.nodeId();
            pickerOpen = false;
        }

        var renderer = ctx.renderer();
        var theme = ctx.theme();
        renderer.drawText("Color", x, renderer.baselineForBox(y, rowHeight), Theme.toArgb(theme.textMuted));

        int valueX = x + labelWidth + theme.design.space_sm;
        int swatchW = Math.max(1, width - (valueX - x));
        int swatchH = rowHeight - 4;
        int swatchY = y + 2;
        int argb = 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
        renderer.drawRoundedRect(valueX, swatchY, swatchW, swatchH, theme.design.radius_sm,
                argb, theme.design.border_thin, Theme.toArgb(theme.widgetOutline));

        var input = ctx.input();
        boolean canInteract = ctx.interactive() && input != null;
        float mx = canInteract ? input.mousePos().x : -1;
        float my = canInteract ? input.mousePos().y : -1;
        boolean hovered = canInteract && mx >= valueX && my >= swatchY && mx < valueX + swatchW && my < swatchY + swatchH;
        if (hovered && input.mousePressed()) {
            pickerOpen = !pickerOpen;
            if (pickerOpen) picker.fromArgb(argb);
        }

        int cursorY = y + rowHeight;
        if (pickerOpen) {
            int pickerHeight = 160;
            int pickerWidth = Math.max(200, width);
            boolean changed = picker.render(renderer, input, theme, x, cursorY, pickerWidth, pickerHeight, ctx.interactive());
            if (changed) {
                int picked = picker.toArgb();
                float nr = ((picked >> 16) & 0xFF) / 255f;
                float ng = ((picked >> 8) & 0xFF) / 255f;
                float nb = (picked & 0xFF) / 255f;
                long node = ctx.nodeId();
                ctx.bridge().commitProperty(node, "color_r", ParseUtils.trimFloat(nr));
                ctx.bridge().commitProperty(node, "color_g", ParseUtils.trimFloat(ng));
                ctx.bridge().commitProperty(node, "color_b", ParseUtils.trimFloat(nb));
            }
            cursorY += pickerHeight + theme.design.space_sm;
        }
        return cursorY;
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
}
