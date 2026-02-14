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

public final class OpsPanel extends Panel {
    private final EditorRuntime runtime;

    public OpsPanel(EditorRuntime runtime) {
        super("Ops");
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

        ui.beginPanel(x, y, w, h);
        int pad = ui.theme().tokens.padding;

        EditorState state = runtime.state();
        if (state.lastAck == null) {
            r.drawText("Output", x + pad, r.baselineForBox(y + pad, 22), Theme.toArgb(theme.textMuted));
            ui.spacer(22);
            r.drawText("(no ops yet)", x + pad, r.baselineForBox(y + pad + 22, 22), Theme.toArgb(theme.textMuted));
            ui.endPanel();
            return;
        }

        String header = "Output  •  batch " + state.lastAck.batchId()
                + "  •  rev " + state.lastAck.sceneRevision()
                + "  •  clientRev " + state.scene.revision()
                + "  •  scene " + state.activeSceneId;
        r.drawText(header, x + pad, r.baselineForBox(y + pad, 22), Theme.toArgb(theme.textMuted));
        ui.spacer(24);

        int listX = x + pad;
        int listY = y + pad + 24;
        int listW = Math.max(1, w - pad * 2);
        int listH = Math.max(1, h - pad * 2 - 24);
        int rowH = 18;
        int contentH = Math.max(1, state.lastAck.results().size() * rowH);

        Ui.ScrollArea area = ui.beginScrollArea(r, "ops", listX, listY, listW, listH, contentH);
        float scroll = area.scrollY();

        int okCol = Theme.mulAlpha(Theme.toArgb(theme.widgetActive), 0.95f);
        int errCol = 0xFFFF5D6C;
        int textCol = Theme.toArgb(theme.textMuted);

        int maxY = listY + listH;
        for (int i = 0; i < state.lastAck.results().size(); i++) {
            int yy = Math.round(listY - scroll + i * rowH);
            if (yy + rowH < listY) {
                continue;
            }
            if (yy > maxY) {
                break;
            }
            var rr = state.lastAck.results().get(i);
            int dot = rr.ok() ? okCol : errCol;

            float baseline = r.baselineForBox(yy, rowH);
            r.drawCircle(listX + 6, yy + rowH / 2.0f, 3.0f, dot);
            String msg = rr.ok()
                    ? ("ok  target=" + rr.targetId())
                    : ("err  " + rr.error() + "  " + rr.message());
            if (rr.createdId() != 0L) {
                msg += "  created=" + rr.createdId();
            }
            r.drawText(msg, listX + 16, baseline, textCol);
        }

        ui.endScrollArea(area);

        ui.endPanel();
    }
}
