package com.moud.client.fabric.editor.panels;

import com.miry.ui.PanelContext;
import com.miry.ui.Ui;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.ConsoleLog;
import com.miry.ui.widgets.StripTabs;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;

public final class BottomPanel extends Panel {
    private final EditorRuntime runtime;
    private final StripTabs tabs = new StripTabs();
    private final StripTabs.Style tabStyle = new StripTabs.Style();
    private final ConsoleLog console = new ConsoleLog();
    private int activeTab;
    private long lastBatchId = Long.MIN_VALUE;
    private long lastSceneRevision = Long.MIN_VALUE;

    public BottomPanel(EditorRuntime runtime) {
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

        ui.beginPanel(x, y, w, h);

        int barH = 32;
        barH = Math.min(barH, Math.max(18, h));
        int barY = y + h - barH;

        int contentH = Math.max(0, barY - y);
        if (contentH > 8 && activeTab == 0) {
            renderOutput(ui, r, theme, x, y, w, contentH);
        }

        renderTabBar(ctx, r, theme, x, barY, w, barH);

        ui.endPanel();
    }

    private void renderTabBar(PanelContext ctx, UiRenderer r, Theme theme, int x, int y, int w, int h) {
        tabStyle.containerBg = Theme.toArgb(theme.windowBg);
        tabStyle.tabActiveBg = Theme.toArgb(theme.widgetHover);
        tabStyle.tabInactiveBg = 0;
        tabStyle.tabHoverBg = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.85f);
        tabStyle.borderColor = Theme.toArgb(theme.headerLine);
        tabStyle.highlightColor = Theme.toArgb(theme.accent);
        tabStyle.textActive = Theme.toArgb(theme.text);
        tabStyle.textInactive = Theme.toArgb(theme.textMuted);
        tabStyle.equalWidth = false;
        tabStyle.highlightTop = false;
        tabStyle.highlightThickness = 0;

        String[] labels = new String[]{"Output"};
        activeTab = tabs.render(r, ctx.uiContext(), ctx.ui().input(), theme, x, y, w, h, labels, 0, true, tabStyle);
    }

    private void renderOutput(Ui ui, UiRenderer r, Theme theme, int x, int y, int w, int h) {
        int pad = theme.design.space_md;
        int cx = x + pad;
        int cy = y + pad;
        int cw = Math.max(1, w - pad * 2);
        int ch = Math.max(1, h - pad * 2);

        EditorState state = runtime.state();
        if (state == null || state.lastAck == null) {
            r.drawText("(no output yet)", cx, r.baselineForBox(cy, 18), Theme.toArgb(theme.textMuted));
            return;
        }

        long batchId = state.lastAck.batchId();
        long sceneRevision = state.lastAck.sceneRevision();
        if (batchId != lastBatchId || sceneRevision != lastSceneRevision) {
            lastBatchId = batchId;
            lastSceneRevision = sceneRevision;
            rebuildConsole(state);
        }

        console.render(r, ui.input(), theme, cx, cy, cw, ch);
    }

    private void rebuildConsole(EditorState state) {
        console.clear();
        if (state == null || state.lastAck == null) {
            return;
        }

        console.info("batch=" + state.lastAck.batchId()
                + " rev=" + state.lastAck.sceneRevision()
                + " clientRev=" + state.scene.revision()
                + " scene=" + state.activeSceneId);

        for (var rr : state.lastAck.results()) {
            if (rr == null) {
                continue;
            }
            if (rr.ok()) {
                String msg = "ok target=" + rr.targetId();
                if (rr.createdId() != 0L) {
                    msg += " created=" + rr.createdId();
                }
                console.info(msg);
            } else {
                console.error("target=" + rr.targetId() + " " + rr.error() + " " + rr.message());
            }
        }
    }
}
