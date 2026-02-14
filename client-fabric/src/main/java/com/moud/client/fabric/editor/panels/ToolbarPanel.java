package com.moud.client.fabric.editor.panels;

import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.theme.EditorTheme;
import com.moud.client.fabric.editor.tools.EditorTool;

import com.miry.ui.PanelContext;
import com.miry.ui.Ui;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.Theme;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;

public final class ToolbarPanel extends Panel {
    private final EditorRuntime runtime;
    private int activeId;

    public ToolbarPanel(EditorRuntime runtime) {
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
        r.drawRect(x, y, w, h, bg);
        r.drawRect(x, y + h - 1, w, 1, Theme.toArgb(theme.headerLine));

        int pad = theme.design.space_sm;
        int iconSize = theme.design.icon_lg;
        int gap = theme.design.space_xs;
        int cursorX = x + pad;
        int btnY = y + (h - iconSize) / 2;

        int badgeSize = 16;
        int badgeY = y + (h - badgeSize) / 2;
        r.drawRoundedRect(cursorX, badgeY, badgeSize, badgeSize, 2.0f, EditorTheme.accent(theme));
        cursorX += badgeSize + theme.design.space_sm;

        r.drawText("MOUD", cursorX, r.baselineForBox(y, h), Theme.toArgb(theme.text));
        cursorX += (int) r.measureText("MOUD") + theme.design.space_md;

        for (EditorTool t : EditorTool.values()) {
            Icon icon = getIconForTool(t);
            int id = (t.name().hashCode() ^ 0x5F356495);
            if (iconButton(ui, r, theme, id, cursorX, btnY, iconSize, icon, runtime.tool() == t)) {
                runtime.setTool(t);
            }
            cursorX += iconSize + gap;
        }

        cursorX += theme.design.space_md;

        int sepY = y + (h - 16) / 2;
        r.drawRect(cursorX, sepY, 1, 16, Theme.toArgb(theme.headerLine));
        cursorX += 1 + theme.design.space_md;

        EditorState state = runtime.state();
        if (state != null && state.scenes != null && !state.scenes.isEmpty()) {
            for (var scene : state.scenes) {
                if (scene == null || scene.sceneId() == null || scene.sceneId().isBlank()) {
                    continue;
                }
                if (cursorX + 60 > x + w - 200) {
                    break;
                }
                String label = truncate(scene.uiLabel(), 16);
                int tabW = Math.max(64, (int) r.measureText(label) + theme.design.space_md * 2);
                boolean selected = scene.sceneId().equals(state.activeSceneId);
                int id = (scene.sceneId().hashCode() ^ 0x2B7B10D1);
                if (sceneTab(ui, r, theme, id, cursorX, y, tabW, h, label, selected)) {
                    runtime.net().selectScene(runtime.session(), runtime.state(), scene.sceneId());
                }
                cursorX += tabW + 1;
            }
        }

        Session session = runtime.session();
        boolean connected = session != null && session.state() == SessionState.CONNECTED;
        var sel = state.scene.getNode(state.selectedId);
        String selName = sel == null ? "No selection" : truncate(sel.name(), 24);

        String status = (connected ? "●" : "○") + " " + selName;
        float statusW = r.measureText(status);
        r.drawText(status, x + w - pad - statusW, r.baselineForBox(y, h), Theme.toArgb(theme.textMuted));
    }

    private boolean iconButton(Ui ui, UiRenderer r, Theme theme, int id, int x, int y, int size, Icon icon, boolean selected) {
        float mx = ui.mouse().x;
        float my = ui.mouse().y;
        boolean hovered = (mx >= x && mx < x + size && my >= y && my < y + size);

        if (hovered && ui.input().mousePressed()) {
            activeId = id;
        }

        boolean clicked = false;
        if (activeId == id && ui.input().mouseReleased()) {
            clicked = hovered;
            activeId = 0;
        }
        if (ui.input().mouseReleased() && activeId == id && !hovered) {
            activeId = 0;
        }

        int fill = selected ? EditorTheme.accent(theme) : Theme.toArgb(theme.headerBg);
        if (hovered) {
            fill = selected
                    ? Theme.lightenArgb(fill, 0.12f)
                    : Theme.lightenArgb(fill, 0.08f);
        }
        if (activeId == id) {
            fill = Theme.darkenArgb(fill, 0.08f);
        }

        r.drawRoundedRect(x, y, size, size, theme.design.radius_sm, fill);

        int iconColor = selected ? 0xFFFFFFFF : Theme.toArgb(theme.text);
        float iconDrawSize = theme.design.icon_sm;
        float iconX = x + (size - iconDrawSize) / 2;
        float iconY = y + (size - iconDrawSize) / 2;
        theme.icons.draw(r, icon, iconX, iconY, iconDrawSize, iconColor);

        return clicked;
    }

    private boolean sceneTab(Ui ui, UiRenderer r, Theme theme, int id, int x, int y, int w, int h, String label, boolean selected) {
        float mx = ui.mouse().x;
        float my = ui.mouse().y;
        boolean hovered = (mx >= x && mx < x + w && my >= y && my < y + h);

        if (hovered && ui.input().mousePressed()) {
            activeId = id;
        }

        boolean clicked = false;
        if (activeId == id && ui.input().mouseReleased()) {
            clicked = hovered;
            activeId = 0;
        }
        if (ui.input().mouseReleased() && activeId == id && !hovered) {
            activeId = 0;
        }

        int fill = selected ? Theme.toArgb(theme.panelBg) : Theme.darkenArgb(Theme.toArgb(theme.headerBg), 0.05f);
        if (!selected && hovered) {
            fill = Theme.lightenArgb(fill, 0.06f);
        }

        r.drawRect(x, y, w, h, fill);

        if (selected) {
            r.drawRect(x, y + h - 2, w, 2, EditorTheme.accent(theme));
        }

        float tw = r.measureText(label);
        int textColor = selected ? Theme.toArgb(theme.text) : Theme.toArgb(theme.textMuted);
        r.drawText(label, x + (w - tw) / 2, r.baselineForBox(y, h), textColor);

        return clicked;
    }

    private Icon getIconForTool(EditorTool tool) {
        return switch (tool) {
            case SELECT -> Icon.SELECT;
            case MOVE -> Icon.MOVE;
            case ROTATE -> Icon.ROTATE;
            case SCALE -> Icon.SCALE;
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (max <= 3 || s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1) + "…";
    }
}