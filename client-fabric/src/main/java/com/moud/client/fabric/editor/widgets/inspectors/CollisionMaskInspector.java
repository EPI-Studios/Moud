package com.moud.client.fabric.editor.widgets.inspectors;

import com.miry.ui.theme.Theme;
import com.moud.client.fabric.editor.widgets.BitmaskGrid;
import com.moud.client.fabric.editor.widgets.InspectorContext;
import com.moud.client.fabric.editor.widgets.NodeInspectorWidget;
import com.moud.core.PropertyDef;
import com.moud.core.util.ParseUtils;

public final class CollisionMaskInspector implements NodeInspectorWidget {
    private final BitmaskGrid layerGrid = new BitmaskGrid();
    private final BitmaskGrid maskGrid = new BitmaskGrid();
    private long lastNodeId;

    @Override
    public boolean handles(String propertyKey) {
        return "collision_layer".equals(propertyKey) || "collision_mask".equals(propertyKey);
    }

    @Override
    public int renderRow(InspectorContext ctx, PropertyDef property, String value,
                         int x, int y, int width, int rowHeight, int labelWidth) {
        BitmaskGrid grid = "collision_layer".equals(property.key()) ? layerGrid : maskGrid;
        String key = property.key();

        int current = (int) ParseUtils.parseFloat(value, 1f);
        if (ctx.nodeId() != lastNodeId) {
            lastNodeId = ctx.nodeId();
            layerGrid.setMask((int) ParseUtils.parseFloat(ctx.values().get("collision_layer"), 1f));
            maskGrid.setMask((int) ParseUtils.parseFloat(ctx.values().get("collision_mask"), 1f));
        } else if (grid.mask() != current && (ctx.input() == null || !ctx.input().mouseDown())) {
            grid.setMask(current);
        }

        long node = ctx.nodeId();
        grid.setListener(m -> ctx.bridge().commitProperty(node, key, Integer.toString(m)));

        var renderer = ctx.renderer();
        var theme = ctx.theme();
        renderer.drawText(property.uiLabel(), x, renderer.baselineForBox(y, rowHeight), Theme.toArgb(theme.textMuted));

        int valueX = x + labelWidth + theme.design.space_sm;
        int valueW = Math.max(1, width - (valueX - x));
        int cellH = grid.cellHeight();
        int next = grid.render(renderer, ctx.input(), theme, valueX, y, valueW, cellH, ctx.interactive());
        return Math.max(y + rowHeight, next + 4);
    }
}
