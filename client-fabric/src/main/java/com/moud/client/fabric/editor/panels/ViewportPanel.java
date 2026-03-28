package com.moud.client.fabric.editor.panels;


import com.moud.net.protocol.SceneInfo;
import com.moud.net.protocol.SceneSnapshot;
import com.miry.ui.PanelContext;
import com.miry.ui.UiContext;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.TabBar;
import com.miry.graphics.Texture;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.tools.EditorGizmos;
import com.moud.client.fabric.editor.tools.EditorTool;
import com.moud.client.fabric.editor.util.EditorUiUtil;
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

        int tabsH = theme.design.tab_height_md;
        int toolbarH = Math.max(24, theme.design.widget_height_md + 2);
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

        int viewBg = Theme.toArgb(theme.windowBg);
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
            r.drawText("(no viewport yet)", viewX + theme.design.space_md, r.baselineForBox(viewY + 8, 24), Theme.toArgb(theme.textMuted));
        }

        if (gizmos != null && viewW > 0 && viewH > 0) {
            EditorState state = runtime.state();
            if (state != null) {
                gizmos.update3DGizmos(state, targetAspect);
            }
            gizmos.render(ctx.ui(), r, viewX, viewY, viewW, viewH);
        }

        int badgeH = theme.design.widget_height_sm;
        int badgeW = 94;
        int bx = viewX + theme.design.space_md;
        int by = viewY + theme.design.space_md;
        int badgeBg = Theme.mulAlpha(Theme.toArgb(theme.widgetBg), 0.55f);
        r.drawRoundedRect(bx, by, badgeW, badgeH, theme.design.radius_sm, badgeBg);
        r.drawText("Perspective", bx + theme.design.space_md, r.baselineForBox(by, badgeH), Theme.toArgb(theme.textMuted));

        EditorState statsState = runtime.state();
        if (statsState != null && statsState.scene != null && viewW > 0 && viewH > 0) {
            int nodeCount = statsState.scene.nodes().size();
            String selectedName = "";
            if (statsState.selectedId > 0L) {
                SceneSnapshot.NodeSnapshot sel = statsState.scene.getNode(statsState.selectedId);
                if (sel != null) {
                    selectedName = "  |  " + sel.name() + " [" + sel.type() + "]";
                }
            }
            String statsText = nodeCount + " node" + (nodeCount != 1 ? "s" : "") + selectedName;
            float statsW = r.measureText(statsText) + 20;
            int statsBadgeH = theme.design.widget_height_sm;
            int statsX = viewX + viewW - (int) statsW - theme.design.space_md;
            int statsY = viewY + viewH - statsBadgeH - theme.design.space_md;
            r.drawRoundedRect(statsX, statsY, (int) statsW, statsBadgeH, theme.design.radius_sm, badgeBg);
            r.drawText(statsText, statsX + theme.design.space_md, r.baselineForBox(statsY, statsBadgeH), Theme.toArgb(theme.textMuted));
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
            tab.pinned = "main".equals(sceneId);
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

        var ui = ctx.ui();
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
        bx = EditorUiUtil.toggleButton(ui, r, theme, bx, by, btn, btn, Icon.SELECT, tool == EditorTool.SELECT, interactive, () -> runtime.setTool(EditorTool.SELECT)) + pad;
        bx = EditorUiUtil.toggleButton(ui, r, theme, bx, by, btn, btn, Icon.MOVE, tool == EditorTool.MOVE, interactive, () -> runtime.setTool(EditorTool.MOVE)) + pad;
        bx = EditorUiUtil.toggleButton(ui, r, theme, bx, by, btn, btn, Icon.ROTATE, tool == EditorTool.ROTATE, interactive, () -> runtime.setTool(EditorTool.ROTATE)) + pad;
        EditorUiUtil.toggleButton(ui, r, theme, bx, by, btn, btn, Icon.SCALE, tool == EditorTool.SCALE, interactive, () -> runtime.setTool(EditorTool.SCALE));

        cursorX += groupW + pad * 2;

        int snapStepW = 52;
        int snapGroupW = groupPad * 2 + btn + pad + snapStepW;
        r.drawRoundedRect(cursorX, groupY, snapGroupW, groupH, theme.design.radius_sm, groupBg);
        int sx = cursorX + groupPad;
        boolean snapEnabled = runtime != null && runtime.gridSnapEnabled();
        sx = EditorUiUtil.toggleButton(ui, r, theme, sx, by, btn, btn, Icon.SNAP, snapEnabled, interactive, () -> {
            if (runtime != null) {
                runtime.setGridSnapEnabled(!runtime.gridSnapEnabled());
            }
        }) + pad;

        float step = runtime == null ? 1.0f : runtime.gridSnapStep();
        String stepLabel = formatSnapStep(step);
        EditorUiUtil.stepButton(ui, r, theme, sx, by, snapStepW, btn, stepLabel, interactive, () -> {
            if (runtime != null) {
                runtime.cycleGridSnapStep();
            }
        });

        cursorX += snapGroupW + pad * 2;

        int rotStepW = 40;
        int rotGroupW = groupPad * 2 + btn + pad + rotStepW;
        r.drawRoundedRect(cursorX, groupY, rotGroupW, groupH, theme.design.radius_sm, groupBg);
        int rx = cursorX + groupPad;
        boolean rotSnapEnabled = runtime != null && runtime.rotationSnapEnabled();
        rx = EditorUiUtil.toggleButton(ui, r, theme, rx, by, btn, btn, Icon.ROTATE, rotSnapEnabled, interactive, () -> {
            if (runtime != null) {
                runtime.setRotationSnapEnabled(!runtime.rotationSnapEnabled());
            }
        }) + pad;
        float rotStep = runtime == null ? 15.0f : runtime.rotationSnapDeg();
        String rotStepLabel = Math.abs(rotStep - 15.0f) < 1e-3f ? "15°"
                : Math.abs(rotStep - 45.0f) < 1e-3f ? "45°" : "90°";
        EditorUiUtil.stepButton(ui, r, theme, rx, by, rotStepW, btn, rotStepLabel, interactive, () -> {
            if (runtime != null) {
                runtime.cycleRotationSnapDeg();
            }
        });

        cursorX += rotGroupW + pad * 2;

        int spaceGroupW = groupPad * 2 + btn;
        r.drawRoundedRect(cursorX, groupY, spaceGroupW, groupH, theme.design.radius_sm, groupBg);
        boolean localSpace = runtime != null && runtime.gizmoLocalSpace();
        EditorUiUtil.toggleButton(ui, r, theme, cursorX + groupPad, by, btn, btn, Icon.ROTATE, localSpace, interactive, () -> {
            if (runtime != null) runtime.setGizmoLocalSpace(!runtime.gizmoLocalSpace());
        });
        int lx = cursorX + groupPad + btn + theme.design.space_sm;
        r.drawText(localSpace ? "Local" : "World", lx, r.baselineForBox(groupY, groupH), Theme.toArgb(localSpace ? theme.text : theme.textMuted));
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

}
