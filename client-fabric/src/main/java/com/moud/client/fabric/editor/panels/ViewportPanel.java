package com.moud.client.fabric.editor.panels;

import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.theme.EditorTheme;
import com.moud.client.fabric.editor.tools.EditorGizmos;
import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;

import com.miry.graphics.Texture;
import com.miry.ui.PanelContext;
import com.miry.ui.Ui;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;

public final class ViewportPanel extends Panel {
    private final EditorRuntime runtime;
    private final EditorGizmos gizmos;

    public ViewportPanel(EditorRuntime runtime, EditorGizmos gizmos) {
        super("Viewport");
        this.runtime = runtime;
        this.gizmos = gizmos;
    }

    @Override
    public void render(PanelContext ctx) {
        Ui ui = ctx.ui();
        UiRenderer r = ctx.renderer();
        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        int h = ctx.height();

        EditorContext editorCtx = EditorOverlayBus.get();
        if (editorCtx != null) {
            editorCtx.setViewportBounds(x, y, w, h);
        }

        Theme theme = ui.theme();
        int bg = 0xFF0F1014;
        r.drawRect(x, y, w, h, bg);

        Texture tex = runtime.viewportTexture();
        if (tex == null || tex.id() == 0 || tex.width() <= 0 || tex.height() <= 0) {
            r.drawText("(no viewport yet)", x + 12, r.baselineForBox(y + 8, 24), Theme.toArgb(theme.textMuted));
            return;
        }
        r.drawTexturedRect(tex, x, y, w, h, 0.0f, 1.0f, 1.0f, 0.0f, 0xFFFFFFFF);

        if (gizmos != null) {
            gizmos.render(ui, r, x, y, w, h);
        }

        int cx = x + w / 2;
        int cy = y + h / 2;
        int cross = Math.max(6, Math.min(12, Math.min(w, h) / 40));
        int crossCol = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.55f);
        r.drawRect(cx - cross, cy, cross - 2, 1, crossCol);
        r.drawRect(cx + 2, cy, cross - 2, 1, crossCol);
        r.drawRect(cx, cy - cross, 1, cross - 2, crossCol);
        r.drawRect(cx, cy + 2, 1, cross - 2, crossCol);

        int pad = theme.tokens.padding;
        String tool = runtime.tool() == null ? "?" : runtime.tool().name();
        String space = ui.input().altDown() ? "LOCAL" : "WORLD";
        String badge = "Viewport  •  " + tex.width() + "×" + tex.height() + "  •  " + tool + "  •  " + space;
        float tw = r.measureText(badge);
        int badgeH = Math.max(18, theme.tokens.itemHeight - 6);
        int badgeW = (int) Math.ceil(tw) + 18;
        int bx = x + pad;
        int by = y + pad;
        int fill = Theme.mulAlpha(Theme.toArgb(theme.headerBg), 0.82f);
        int outline = Theme.mulAlpha(Theme.toArgb(theme.headerLine), 0.90f);
        r.drawRoundedRect(bx, by, badgeW, badgeH, theme.design.radius_sm, fill, theme.design.border_thin, outline);
        r.drawText(badge, bx + 10, r.baselineForBox(by, badgeH), Theme.toArgb(theme.text));
    }
}
