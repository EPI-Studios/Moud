package com.moud.client.fabric.editor.panels;

import com.miry.graphics.Texture;
import com.miry.ui.PanelContext;
import com.miry.ui.UiContext;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.StripTabs;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.tools.EditorGizmos;
import com.moud.client.fabric.editor.tools.EditorTool;

public final class ViewportPanel extends Panel {
    private final EditorRuntime runtime;
    private final EditorGizmos gizmos;

    private final StripTabs sceneTabs = new StripTabs();
    private final StripTabs.Style sceneTabStyle = new StripTabs.Style();
    private int activeSceneTab;

    public ViewportPanel(EditorRuntime runtime, EditorGizmos gizmos) {
        super("");
        this.runtime = runtime;
        this.gizmos = gizmos;
    }

    @Override
    public void render(PanelContext ctx) {
        UiRenderer r = ctx.renderer();
        Theme theme = ctx.ui().theme();
        UiContext uiContext = ctx.uiContext();

        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        int h = ctx.height();

        int tabsH = 28;
        int toolbarH = 30;
        int renderY = y + tabsH + toolbarH;
        int renderH = Math.max(0, y + h - renderY);

        EditorContext editorCtx = EditorOverlayBus.get();
        if (editorCtx != null) {
            editorCtx.setViewportBounds(x, renderY, w, renderH);
        }

        renderSceneTabs(ctx, r, uiContext, theme, x, y, w, tabsH);

        renderViewportToolbar(ctx, r, uiContext, theme, x, y + tabsH, w, toolbarH);

        int viewBg = 0xFF15171B;
        r.drawRect(x, renderY, w, renderH, viewBg);

        Texture tex = runtime.viewportTexture();
        if (tex != null && tex.id() != 0 && tex.width() > 0 && tex.height() > 0 && renderH > 0) {
            r.drawTexturedRect(tex, x, renderY, w, renderH, 0.0f, 1.0f, 1.0f, 0.0f, 0xFFFFFFFF);
        } else {
            r.drawText("(no viewport yet)", x + 12, r.baselineForBox(renderY + 8, 24), Theme.toArgb(theme.textMuted));
        }

        if (gizmos != null && renderH > 0) {
            gizmos.render(ctx.ui(), r, x, renderY, w, renderH);
        }

        int badgeH = 20;
        int badgeW = 94;
        int bx = x + 10;
        int by = renderY + 10;
        int badgeBg = Theme.mulAlpha(0xFF000000, 0.25f);
        r.drawRoundedRect(bx, by, badgeW, badgeH, theme.design.radius_sm, badgeBg);
        r.drawText("Perspective", bx + 10, r.baselineForBox(by, badgeH), Theme.toArgb(theme.textMuted));
    }

    private void renderSceneTabs(PanelContext ctx, UiRenderer r, UiContext uiContext, Theme theme, int x, int y, int w, int h) {
        int container = Theme.toArgb(theme.headerLine);
        r.drawRect(x, y, w, h, container);
        r.drawRect(x, y + h - 1, w, 1, Theme.toArgb(theme.headerLine));

        EditorState state = runtime.state();
        if (state == null || state.scenes == null || state.scenes.isEmpty()) {
            r.drawText("(no scenes)", x + 10, r.baselineForBox(y, h), Theme.toArgb(theme.textMuted));
            return;
        }

        int count = Math.min(10, state.scenes.size());
        String[] labels = new String[count];
        String[] ids = new String[count];
        for (int i = 0; i < count; i++) {
            var s = state.scenes.get(i);
            String id = s != null ? s.sceneId() : null;
            if (id == null || id.isBlank()) {
                id = "scene-" + i;
            }
            ids[i] = id;
            String label = s != null ? s.uiLabel() : id;
            labels[i] = (label == null || label.isBlank()) ? id : label;
        }

        int selected = 0;
        String activeId = state.activeSceneId;
        if (activeId != null) {
            for (int i = 0; i < ids.length; i++) {
                if (activeId.equals(ids[i])) {
                    selected = i;
                    break;
                }
            }
        }

        sceneTabStyle.containerBg = 0;
        sceneTabStyle.tabActiveBg = Theme.toArgb(theme.windowBg);
        sceneTabStyle.tabInactiveBg = Theme.toArgb(theme.headerBg);
        sceneTabStyle.tabHoverBg = Theme.toArgb(theme.widgetHover);
        sceneTabStyle.borderColor = 0;
        sceneTabStyle.highlightColor = 0;
        sceneTabStyle.textActive = Theme.toArgb(theme.text);
        sceneTabStyle.textInactive = Theme.toArgb(theme.textMuted);
        sceneTabStyle.equalWidth = false;
        sceneTabStyle.highlightThickness = 0;

        int tabsW = Math.max(1, w - 30);
        activeSceneTab = sceneTabs.render(r, uiContext, ctx.ui().input(), theme, x + 4, y + 4, tabsW, h - 4, labels, selected, true, sceneTabStyle);
        if (activeSceneTab != selected && activeSceneTab >= 0 && activeSceneTab < ids.length) {
            selectScene(ids[activeSceneTab]);
        }
    }

    private void renderViewportToolbar(PanelContext ctx, UiRenderer r, UiContext uiContext, Theme theme, int x, int y, int w, int h) {
        int bg = Theme.toArgb(theme.windowBg);
        r.drawRect(x, y, w, h, bg);
        r.drawRect(x, y + h - 1, w, 1, Theme.toArgb(theme.headerLine));

        int pad = theme.design.space_sm;
        int cursorX = x + pad;
        int btn = 22;
        int groupPad = 2;

        int groupW = btn * 4 + pad * 3;
        int groupH = btn + groupPad * 2;
        int groupY = y + (h - groupH) / 2;
        int groupBg = Theme.toArgb(theme.headerBg);
        r.drawRoundedRect(cursorX, groupY, groupW, groupH, theme.design.radius_sm, groupBg);

        int bx = cursorX + groupPad;
        int by = groupY + groupPad;
        EditorTool tool = runtime.tool();
        bx = renderToolToggle(ctx, r, theme, bx, by, btn, btn, Icon.SELECT, tool == EditorTool.SELECT, () -> runtime.setTool(EditorTool.SELECT)) + pad;
        bx = renderToolToggle(ctx, r, theme, bx, by, btn, btn, Icon.MOVE, tool == EditorTool.MOVE, () -> runtime.setTool(EditorTool.MOVE)) + pad;
        bx = renderToolToggle(ctx, r, theme, bx, by, btn, btn, Icon.ROTATE, tool == EditorTool.ROTATE, () -> runtime.setTool(EditorTool.ROTATE)) + pad;
        renderToolToggle(ctx, r, theme, bx, by, btn, btn, Icon.SCALE, tool == EditorTool.SCALE, () -> runtime.setTool(EditorTool.SCALE));

        cursorX += groupW + pad * 2;
    }

    private void selectScene(String sceneId) {
        if (sceneId == null || sceneId.isBlank()) {
            return;
        }
        EditorState state = runtime.state();
        EditorNet net = runtime.net();
        if (net == null) {
            return;
        }
        net.selectScene(runtime.session(), state, sceneId);
    }

    private static int renderToolToggle(PanelContext ctx,
                                        UiRenderer r,
                                        Theme theme,
                                        int x,
                                        int y,
                                        int w,
                                        int h,
                                        Icon icon,
                                        boolean active,
                                        Runnable action) {
        var input = ctx.ui().input();
        int fill = 0;
        if (active) {
            fill = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.95f);
        }
        if (fill != 0) {
            r.drawRoundedRect(x, y, w, h, theme.design.radius_sm, fill);
        }
        int col = active ? Theme.toArgb(theme.accent) : Theme.toArgb(theme.textMuted);
        float iconSize = Math.min(theme.design.icon_sm, h - 6);
        theme.icons.draw(r, icon, x + (w - iconSize) * 0.5f, y + (h - iconSize) * 0.5f, iconSize, col);

        boolean canInteract = input != null;
        float mx = canInteract ? input.mousePos().x : -1;
        float my = canInteract ? input.mousePos().y : -1;
        boolean hovered = canInteract && mx >= x && my >= y && mx < x + w && my < y + h;
        if (hovered && canInteract && input.mouseReleased() && action != null) {
            action.run();
        }
        return x + w;
    }

}
