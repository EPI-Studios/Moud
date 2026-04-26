package com.moud.client.fabric.editor.widgets.inspectors;

import com.miry.ui.theme.Theme;
import com.moud.client.fabric.editor.widgets.InspectorContext;
import com.moud.client.fabric.editor.widgets.NodeInspectorWidget;
import com.moud.client.fabric.editor.widgets.SliderWithNumber;
import com.moud.core.PropertyDef;
import com.moud.core.util.ParseUtils;

public final class Camera3DInspector implements NodeInspectorWidget {
    private final SliderWithNumber fovSlider = new SliderWithNumber(70f, 1f, 179f);
    private long lastNodeId;

    public Camera3DInspector() {
        fovSlider.setSnapStep(1f);
    }

    @Override
    public boolean handles(String propertyKey) {
        return "fov".equals(propertyKey);
    }

    @Override
    public int renderRow(InspectorContext ctx, PropertyDef property, String value,
                         int x, int y, int width, int rowHeight, int labelWidth) {
        float current = ParseUtils.parseFloat(value, 70f);
        if (ctx.nodeId() != lastNodeId) {
            lastNodeId = ctx.nodeId();
            fovSlider.setValue(current);
        } else if (Math.abs(fovSlider.value() - current) > 0.01f && !ctx.input().mouseDown()) {
            fovSlider.setValue(current);
        }

        long captured = ctx.nodeId();
        fovSlider.setListener(v -> ctx.bridge().commitProperty(captured, property.key(), ParseUtils.trimFloat(v)));

        ctx.renderer().drawText(property.uiLabel(), x,
                ctx.renderer().baselineForBox(y, rowHeight),
                Theme.mulAlpha(Theme.toArgb(ctx.theme().textMuted), 0.86f));

        int valueX = x + labelWidth + ctx.theme().design.space_sm;
        int valueW = Math.max(40, width - (valueX - x));
        fovSlider.render(ctx.renderer(), ctx.uiContext(), ctx.input(), ctx.theme(),
                valueX, y, valueW, rowHeight, ctx.interactive());
        return y + rowHeight;
    }
}
