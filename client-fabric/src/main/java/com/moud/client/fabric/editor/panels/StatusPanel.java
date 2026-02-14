package com.moud.client.fabric.editor.panels;

import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.theme.EditorTheme;

import com.miry.ui.PanelContext;
import com.miry.ui.Ui;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;

public final class StatusPanel extends Panel {
    private final EditorRuntime runtime;

    public StatusPanel(EditorRuntime runtime) {
        super("");
        this.runtime = runtime;
    }

    @Override
    public void render(PanelContext ctx) {
        Ui ui = ctx.ui();
        UiRenderer r = ctx.renderer();
        Theme theme = ui.theme();

        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        int h = ctx.height();

        int bg = Theme.toArgb(theme.headerBg);
        int outline = Theme.toArgb(theme.headerLine);
        int top = Theme.lightenArgb(bg, 0.04f);
        int bottom = Theme.darkenArgb(bg, 0.06f);
        r.drawLinearGradientRoundedRect(x, y, w, h, 0.0f, top, bottom, 0.0f, 1.0f, theme.design.border_thin, outline);

        int pad = Math.max(6, theme.tokens.padding);
        float baseline = r.baselineForBox(y, h);

        EditorState state = runtime.state();
        String rev = "rev " + state.scene.revision();

        Session session = runtime.session();
        String conn = session != null && session.state() == SessionState.CONNECTED ? "connected" : "disconnected";

        String selected = "";
        var sel = state.scene.getNode(state.selectedId);
        if (sel != null) {
            selected = "selected: " + sel.name();
        }

        String hint = "F8 editor  •  Enter apply  •  Scroll lists";
        String left = conn + "  •  " + rev + (selected.isEmpty() ? "" : "  •  " + selected);

        r.drawText(left, x + pad, baseline, Theme.toArgb(theme.textMuted));

        float rightW = r.measureText(hint);
        if (rightW + pad * 2 < w) {
            r.drawText(hint, x + w - pad - rightW, baseline, Theme.toArgb(theme.textMuted));
        }
    }
}
