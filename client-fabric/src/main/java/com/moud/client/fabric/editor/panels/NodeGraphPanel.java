package com.moud.client.fabric.editor.panels;


import com.miry.ui.PanelContext;
import com.miry.ui.Ui;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.core.scene.Node;

public final class NodeGraphPanel extends Panel {
    @SuppressWarnings("unused")
    private final EditorRuntime runtime;

    public NodeGraphPanel(EditorRuntime runtime) {
        super("Node Graph");
        this.runtime = runtime;
    }

    @Override
    public void render(PanelContext ctx) {
        Ui ui = ctx.ui();
        UiRenderer r = ctx.renderer();
        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        int h = ctx.height();
        Theme theme = ui.theme();

        ui.beginPanel(x, y, w, h);
        int pad = theme.tokens.padding;
        int rowH = theme.design.widget_height_md;
        int rowStep = theme.design.widget_height_md + theme.design.space_xs;
        r.drawText("Node Graph", x + pad, r.baselineForBox(y + pad, rowH), Theme.toArgb(theme.textMuted));
        ui.spacer(rowStep);

        int col = Theme.toArgb(theme.textMuted);
        r.drawText("(coming soon)", x + pad, r.baselineForBox(y + pad + rowStep, rowH), col);
        ui.endPanel();
    }
}
