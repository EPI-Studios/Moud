package com.moud.client.fabric.editor.panels;


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
        r.drawText("Node Graph", x + pad, r.baselineForBox(y + pad, 22), Theme.toArgb(theme.textMuted));
        ui.spacer(26);

        int col = Theme.toArgb(theme.textMuted);
        r.drawText("(coming soon)", x + pad, r.baselineForBox(y + pad + 26, 22), col);
        ui.endPanel();
    }
}
