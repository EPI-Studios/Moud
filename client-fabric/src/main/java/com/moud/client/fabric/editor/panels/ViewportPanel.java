package com.moud.client.fabric.editor.panels;

import com.miry.graphics.Texture;
import com.miry.ui.PanelContext;
import com.miry.ui.UiContext;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.TabBar;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.tools.EditorGizmos;
import com.moud.client.fabric.editor.tools.EditorTool;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import com.moud.net.protocol.SceneInfo;

import java.util.ArrayList;
import java.util.HashMap;

public final class ViewportPanel extends Panel {
    private final EditorRuntime runtime;
    private final EditorGizmos gizmos;

    private final TabBar sceneTabs = new TabBar();

    public ViewportPanel(EditorRuntime runtime, EditorGizmos gizmos) {
        super("");
        this.runtime = runtime;
        this.gizmos = gizmos;
        sceneTabs.setOnTabClose((index, tab) -> closeSceneTab(index, tab));
    }

    @Override
    public void render(PanelContext ctx) {
        UiRenderer r = ctx.renderer();
        Theme theme = ctx.ui().theme();
        UiContext uiContext = ctx.uiContext();
        boolean interactive = runtime != null && !runtime.uiBlocked();

        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        int h = ctx.height();

        int tabsH = 28;
        int toolbarH = 30;
        int renderY = y + tabsH + toolbarH;
        int renderH = Math.max(0, y + h - renderY);

        float targetAspect = 16.0f / 9.0f;
        int viewX = x;
        int viewY = renderY;
        int viewW = w;
        int viewH = renderH;
        if (w > 0 && renderH > 0) {
            float availAspect = w / (float) renderH;
            if (availAspect > targetAspect) {
                viewH = renderH;
                viewW = Math.round(viewH * targetAspect);
                viewX = x + (w - viewW) / 2;
                viewY = renderY;
            } else {
                viewW = w;
                viewH = Math.round(viewW / targetAspect);
                viewX = x;
                viewY = renderY + (renderH - viewH) / 2;
            }
        }

        EditorContext editorCtx = EditorOverlayBus.get();
        if (editorCtx != null) {
            editorCtx.setViewportBounds(viewX, viewY, viewW, viewH);
        }

        renderSceneTabs(ctx, r, uiContext, theme, x, y, w, tabsH, interactive);

        renderViewportToolbar(ctx, r, uiContext, theme, x, y + tabsH, w, toolbarH, interactive);

        int viewBg = 0xFF15171B;
        r.drawRect(x, renderY, w, renderH, viewBg);

        Texture tex = runtime.viewportTexture();
        if (tex != null && tex.id() != 0 && tex.width() > 0 && tex.height() > 0 && viewW > 0 && viewH > 0) {
            float u0 = 0.0f;
            float u1 = 1.0f;
            float vMin = 0.0f;
            float vMax = 1.0f;
            float texAspect = tex.width() / (float) tex.height();

            if (texAspect > targetAspect + 1e-4f) {
                float frac = targetAspect / texAspect;
                float pad = (1.0f - frac) * 0.5f;
                u0 = pad;
                u1 = 1.0f - pad;
            } else if (texAspect < targetAspect - 1e-4f) {
                float frac = texAspect / targetAspect;
                float pad = (1.0f - frac) * 0.5f;
                vMin = pad;
                vMax = 1.0f - pad;
            }

            r.drawTexturedRect(tex, viewX, viewY, viewW, viewH, u0, vMax, u1, vMin, 0xFFFFFFFF);
        } else if (viewW > 0 && viewH > 0) {
            r.drawText("(no viewport yet)", viewX + 12, r.baselineForBox(viewY + 8, 24), Theme.toArgb(theme.textMuted));
        }

        if (gizmos != null && viewW > 0 && viewH > 0) {
            EditorState state = runtime.state();
            if (state != null) {
                gizmos.update3DGizmos(state, targetAspect);
            }
            gizmos.render(ctx.ui(), r, viewX, viewY, viewW, viewH);
        }

        int badgeH = 20;
        int badgeW = 94;
        int bx = viewX + 10;
        int by = viewY + 10;
        int badgeBg = Theme.mulAlpha(0xFF000000, 0.25f);
        r.drawRoundedRect(bx, by, badgeW, badgeH, theme.design.radius_sm, badgeBg);
        r.drawText("Perspective", bx + 10, r.baselineForBox(by, badgeH), Theme.toArgb(theme.textMuted));

        PlayRuntimeClient playRuntime = PlayRuntimeBus.get();
        if (playRuntime != null && playRuntime.isActive() && !playRuntime.hasCamera() && viewW > 0 && viewH > 0) {
            r.drawRect(viewX, viewY, viewW, viewH, 0xCC000000);
            String msg = "No Camera3D in scene";
            int msgW = Math.round(r.measureText(msg));
            int msgX = viewX + (viewW - msgW) / 2;
            int msgY = viewY + viewH / 2;
            r.drawText(msg, msgX, r.baselineForBox(msgY - 10, 20), Theme.toArgb(theme.danger));
        }
    }

    private void renderSceneTabs(PanelContext ctx, UiRenderer r, UiContext uiContext, Theme theme, int x, int y, int w, int h, boolean interactive) {
        int bg = Theme.toArgb(theme.headerBg);
        r.drawRect(x, y, w, h, bg);
        r.drawRect(x, y + h - 1, w, 1, Theme.toArgb(theme.headerLine));

        EditorState state = runtime.state();
        if (state == null || state.scenes == null || state.scenes.isEmpty()) {
            r.drawText("(no scenes)", x + 10, r.baselineForBox(y, h), Theme.toArgb(theme.textMuted));
            return;
        }

        var input = interactive ? ctx.ui().input() : null;
        boolean canInteract = input != null;
        float mx = canInteract ? input.mousePos().x : -1;
        float my = canInteract ? input.mousePos().y : -1;

        String draggedSceneId = runtime.sceneDragId();
        boolean hoveringTabBar = canInteract && mx >= x && my >= y && mx < x + w && my < y + h;
        if (state != null && runtime.sceneDragActive() && hoveringTabBar && canInteract && input.mouseReleased()) {
            if (draggedSceneId != null && !draggedSceneId.isBlank()) {
                state.ensureSceneOpen(draggedSceneId);
                selectScene(draggedSceneId);
            }
        }

        if (runtime.sceneDragActive() && hoveringTabBar) {
            int hl = Theme.mulAlpha(Theme.toArgb(theme.accent), 0.10f);
            r.drawRect(x, y, w, h, hl);
        }

        syncSceneTabs(state);
        int before = sceneTabs.activeIndex();
        sceneTabs.render(r, uiContext, input, theme, x, y, w, h, true);

        syncOpenSceneOrderFromTabs(state);
        int after = sceneTabs.activeIndex();
        if (after != before) {
            String id = tabSceneId(after);
            if (id != null && !id.isBlank()) {
                selectScene(id);
            }
        }
    }

    private void syncSceneTabs(EditorState state) {
        if (state == null) {
            return;
        }

        if (state.openSceneIds.isEmpty()) {
            state.openSceneIds.add("main");
        }
        if (!state.openSceneIds.contains("main")) {
            state.openSceneIds.add(0, "main");
        }

        HashMap<String, String> labels = new HashMap<>();
        if (state.scenes != null) {
            for (SceneInfo info : state.scenes) {
                if (info == null || info.sceneId() == null || info.sceneId().isBlank()) {
                    continue;
                }
                String label = info.uiLabel();
                labels.put(info.sceneId(), (label == null || label.isBlank()) ? info.sceneId() : label);
            }
        }

        var tabs = sceneTabs.tabs();
        tabs.clear();
        for (String sceneId : state.openSceneIds) {
            if (sceneId == null || sceneId.isBlank()) {
                continue;
            }
            String label = labels.getOrDefault(sceneId, sceneId);
            if (state.isDirty() && sceneId.equals(state.activeSceneId)) {
                label = "* " + label;
            }
            TabBar.Tab tab = new TabBar.Tab(label);
            tab.userData = sceneId;
            tab.closable = !"main".equals(sceneId);
            tabs.add(tab);
        }

        int activeIndex = -1;
        if (state.activeSceneId != null && !state.activeSceneId.isBlank()) {
            for (int i = 0; i < tabs.size(); i++) {
                if (state.activeSceneId.equals(tabs.get(i).userData)) {
                    activeIndex = i;
                    break;
                }
            }
        }
        if (activeIndex < 0 && !tabs.isEmpty()) {
            activeIndex = 0;
        }
        sceneTabs.setActiveIndex(activeIndex);
    }

    private void syncOpenSceneOrderFromTabs(EditorState state) {
        if (state == null) {
            return;
        }
        var tabs = sceneTabs.tabs();
        if (tabs == null || tabs.isEmpty()) {
            if (state.openSceneIds.isEmpty()) {
                state.openSceneIds.add("main");
            }
            return;
        }

        ArrayList<String> next = new ArrayList<>(tabs.size());
        for (TabBar.Tab tab : tabs) {
            if (tab == null) {
                continue;
            }
            Object data = tab.userData;
            if (!(data instanceof String sceneId) || sceneId.isBlank()) {
                continue;
            }
            if (!next.contains(sceneId)) {
                next.add(sceneId);
            }
        }
        if (!next.contains("main")) {
            next.add(0, "main");
        }
        if (!next.equals(state.openSceneIds)) {
            state.openSceneIds.clear();
            state.openSceneIds.addAll(next);
        }
    }

    private String tabSceneId(int index) {
        if (index < 0) {
            return null;
        }
        var tabs = sceneTabs.tabs();
        if (tabs == null || index >= tabs.size()) {
            return null;
        }
        Object data = tabs.get(index).userData;
        if (!(data instanceof String sceneId)) {
            return null;
        }
        return sceneId;
    }

    private void closeSceneTab(int index, TabBar.Tab tab) {
        if (tab == null || tab.userData == null) {
            return;
        }
        if (!(tab.userData instanceof String sceneId) || sceneId.isBlank()) {
            return;
        }
        if ("main".equals(sceneId)) {
            return;
        }

        EditorState state = runtime.state();
        if (state == null) {
            return;
        }

        int closedIndex = state.openSceneIds.indexOf(sceneId);
        state.openSceneIds.removeIf(id -> sceneId.equals(id));
        if (state.openSceneIds.isEmpty()) {
            state.openSceneIds.add("main");
        }

        if (!sceneId.equals(state.activeSceneId)) {
            return;
        }

        int preferred = closedIndex >= 0 ? closedIndex : Math.max(0, index);
        preferred = Math.max(0, Math.min(preferred, state.openSceneIds.size() - 1));
        String next = state.openSceneIds.get(preferred);
        if (next == null || next.isBlank()) {
            next = "main";
        }
        state.ensureSceneOpen(next);
        selectScene(next);
    }

    private void renderViewportToolbar(PanelContext ctx, UiRenderer r, UiContext uiContext, Theme theme, int x, int y, int w, int h, boolean interactive) {
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
        bx = renderToolToggle(ctx, r, theme, bx, by, btn, btn, Icon.SELECT, tool == EditorTool.SELECT, interactive, () -> runtime.setTool(EditorTool.SELECT)) + pad;
        bx = renderToolToggle(ctx, r, theme, bx, by, btn, btn, Icon.MOVE, tool == EditorTool.MOVE, interactive, () -> runtime.setTool(EditorTool.MOVE)) + pad;
        bx = renderToolToggle(ctx, r, theme, bx, by, btn, btn, Icon.ROTATE, tool == EditorTool.ROTATE, interactive, () -> runtime.setTool(EditorTool.ROTATE)) + pad;
        renderToolToggle(ctx, r, theme, bx, by, btn, btn, Icon.SCALE, tool == EditorTool.SCALE, interactive, () -> runtime.setTool(EditorTool.SCALE));

        cursorX += groupW + pad * 2;

        int snapStepW = 52;
        int snapGroupW = groupPad * 2 + btn + pad + snapStepW;
        r.drawRoundedRect(cursorX, groupY, snapGroupW, groupH, theme.design.radius_sm, groupBg);
        int sx = cursorX + groupPad;
        boolean snapEnabled = runtime != null && runtime.gridSnapEnabled();
        sx = renderToolToggle(ctx, r, theme, sx, by, btn, btn, Icon.SNAP, snapEnabled, interactive, () -> {
            if (runtime != null) {
                runtime.setGridSnapEnabled(!runtime.gridSnapEnabled());
            }
        }) + pad;

        float step = runtime == null ? 1.0f : runtime.gridSnapStep();
        String stepLabel = formatSnapStep(step);
        renderStepButton(ctx, r, theme, sx, by, snapStepW, btn, stepLabel, interactive, () -> {
            if (runtime != null) {
                runtime.cycleGridSnapStep();
            }
        });

        cursorX += snapGroupW + pad * 2;
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
                                        boolean interactive,
                                        Runnable action) {
        var input = interactive ? ctx.ui().input() : null;
        boolean canInteract = input != null;
        float mx = canInteract ? input.mousePos().x : -1;
        float my = canInteract ? input.mousePos().y : -1;
        boolean hovered = canInteract && mx >= x && my >= y && mx < x + w && my < y + h;

        int fill = 0;
        if (active) {
            fill = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.95f);
        } else if (hovered) {
            fill = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.60f);
        }
        if (fill != 0) {
            r.drawRoundedRect(x, y, w, h, theme.design.radius_sm, fill);
        }

        int col = active
                ? Theme.toArgb(theme.accent)
                : (hovered ? Theme.toArgb(theme.text) : Theme.toArgb(theme.textMuted));
        float iconSize = Math.min(theme.design.icon_sm, h - 6);
        theme.icons.draw(r, icon, x + (w - iconSize) * 0.5f, y + (h - iconSize) * 0.5f, iconSize, col);

        if (hovered && canInteract && input.mouseReleased() && action != null) {
            action.run();
        }
        return x + w;
    }

    private static String formatSnapStep(float step) {
        if (!Float.isFinite(step) || step <= 0.0f) {
            return "1m";
        }
        if (Math.abs(step - 1.0f) < 1e-6f) {
            return "1m";
        }
        if (Math.abs(step - 0.5f) < 1e-6f) {
            return "0.5m";
        }
        if (Math.abs(step - 0.1f) < 1e-6f) {
            return "0.1m";
        }
        String s = Float.toString(step);
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        return s + "m";
    }

    private static void renderStepButton(PanelContext ctx,
                                         UiRenderer r,
                                         Theme theme,
                                         int x,
                                         int y,
                                         int w,
                                         int h,
                                         String label,
                                         boolean interactive,
                                         Runnable action) {
        var input = interactive ? ctx.ui().input() : null;
        boolean canInteract = input != null;
        float mx = canInteract ? input.mousePos().x : -1;
        float my = canInteract ? input.mousePos().y : -1;
        boolean hovered = canInteract && mx >= x && my >= y && mx < x + w && my < y + h;

        int fill = 0;
        if (hovered) {
            fill = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.60f);
        }
        if (fill != 0) {
            r.drawRoundedRect(x, y, w, h, theme.design.radius_sm, fill);
        }

        String text = label == null ? "" : label;
        int col = hovered ? Theme.toArgb(theme.text) : Theme.toArgb(theme.textMuted);
        int textW = Math.round(r.measureText(text));
        int tx = x + Math.max(0, (w - textW) / 2);
        r.drawText(text, tx, r.baselineForBox(y, h), col);

        if (hovered && canInteract && input.mouseReleased() && action != null) {
            action.run();
        }
    }

}
