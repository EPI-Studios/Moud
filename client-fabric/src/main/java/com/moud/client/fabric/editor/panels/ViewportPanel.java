package com.moud.client.fabric.editor.panels;


import com.moud.net.protocol.SceneInfo;
import com.moud.net.protocol.SceneSnapshot;
import com.miry.ui.PanelContext;
import com.miry.ui.UiContext;
import com.miry.ui.dnd.DropTarget;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.CanvasEditor2D;
import com.miry.ui.widgets.TabBar;
import com.miry.graphics.Texture;
import com.moud.client.fabric.editor.dialogs.ScriptEditorDialog;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.tools.EditorGizmos;
import com.moud.client.fabric.editor.util.EditorDnD;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Consumer;

public final class ViewportPanel extends Panel {
    private record SceneTabData(String sceneId) {}
    private record ScriptTabData(String path) {}
    private record TextAssetTabData(String path) {}

    private final EditorRuntime runtime;
    private final EditorGizmos gizmos;

    private final TabBar sceneTabs = new TabBar();
    private Consumer<String> onScriptTabTearOff;

    private final CanvasEditor2D canvas2d = new CanvasEditor2D();
    private final HashMap<Long, SceneCanvasObject> canvasObjectsById = new HashMap<>();
    private final Canvas2DSync canvas2dSync;
    private final ViewportToolbar viewportToolbar;

    public ViewportPanel(EditorRuntime runtime, EditorGizmos gizmos) {
        super("");
        this.runtime = runtime;
        this.gizmos = gizmos;
        sceneTabs.setOnTabClose((index, tab) -> closeTab(index, tab));
        sceneTabs.setOnTabTearOff((index, tab) -> {
            if (tab.userData instanceof ScriptTabData sd && onScriptTabTearOff != null) {
                onScriptTabTearOff.accept(sd.path());
            }
        });

        canvas2d.setZoomLimits(0.02f, 4096.0f);
        canvas2dSync = new Canvas2DSync(runtime, canvas2d, canvasObjectsById);
        viewportToolbar = new ViewportToolbar(runtime);
        canvas2d.setTransformCommitListener(canvas2dSync::onCanvasTransformCommitted);
    }

    public void setOnScriptTabTearOff(Consumer<String> callback) {
        this.onScriptTabTearOff = callback;
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

        EditorState state = runtime != null ? runtime.state() : null;

        renderTabs(ctx, r, uiContext, theme, x, y, w, tabsH, interactive);

        String activeScript = state != null ? state.activeScriptPath : "";
        boolean scriptTabActive = activeScript != null && !activeScript.isBlank();

        if (scriptTabActive) {
            ScriptEditorDialog dialog = runtime.scriptEditorDialog();
            if (dialog != null) {
                int contentY = y + tabsH;
                int contentH = Math.max(0, h - tabsH);
                dialog.renderInline(r, uiContext, ctx.ui(), theme, x, contentY, w, contentH);
            }
            return;
        }

        String activeTextAsset = state != null ? state.activeTextAssetPath : "";
        boolean textAssetTabActive = activeTextAsset != null && !activeTextAsset.isBlank();

        if (textAssetTabActive) {
            var dialog = runtime.textAssetEditorDialog();
            if (dialog != null && dialog.isOpen()) {
                int contentY = y + tabsH;
                int contentH = Math.max(0, h - tabsH);
                dialog.renderInline(r, uiContext, ctx.ui(), theme, x, contentY, w, contentH);
            }
            return;
        }

        int toolbarH = Math.max(24, theme.design.widget_height_md + 2);
        int renderY = y + tabsH + toolbarH;
        int renderH = Math.max(0, y + h - renderY);

        boolean is2D = runtime != null && runtime.viewportMode() == EditorRuntime.ViewportMode.TWO_D;
        float targetAspect = 16.0f / 9.0f;
        int viewX = x;
        int viewY = renderY;
        int viewW = w;
        int viewH = renderH;
        if (!is2D && w > 0 && renderH > 0) {
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

        viewportToolbar.renderViewportToolbar(ctx, r, uiContext, theme, x, y + tabsH, w, toolbarH, interactive);

        int viewBg = Theme.toArgb(theme.windowBg);
        r.drawRect(x, renderY, w, renderH, viewBg);

        if (runtime != null && runtime.viewportMode() == EditorRuntime.ViewportMode.TWO_D && viewW > 0 && viewH > 0) {
            canvas2dSync.render2DCanvas(ctx.ui(), r, uiContext, theme, viewX, viewY, viewW, viewH, interactive);

            int badgeH = theme.design.widget_height_sm;
            int badgeW = 56;
            int bx = viewX + theme.design.space_md;
            int by = viewY + theme.design.space_md;
            int badgeBg = Theme.mulAlpha(Theme.toArgb(theme.widgetBg), 0.55f);
            r.drawRoundedRect(bx, by, badgeW, badgeH, theme.design.radius_sm, badgeBg);
            r.drawText("2D", bx + theme.design.space_md, r.baselineForBox(by, badgeH), Theme.toArgb(theme.textMuted));

            if (state != null && state.scene != null) {
                int nodeCount = state.scene.nodes().size();
                String selectedName = "";
                if (state.selectedId > 0L) {
                    SceneSnapshot.NodeSnapshot sel = state.scene.getNode(state.selectedId);
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
            return;
        }

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

        if (state != null && state.scene != null && viewW > 0 && viewH > 0) {
            int nodeCount = state.scene.nodes().size();
            String selectedName = "";
            if (state.selectedId > 0L) {
                SceneSnapshot.NodeSnapshot sel = state.scene.getNode(state.selectedId);
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

    private void renderTabs(PanelContext ctx, UiRenderer r, UiContext uiContext, Theme theme, int x, int y, int w, int h, boolean interactive) {
        int bg = Theme.toArgb(theme.headerBg);
        r.drawRect(x, y, w, h, bg);
        r.drawRect(x, y + h - 1, w, 1, Theme.toArgb(theme.headerLine));

        EditorState state = runtime.state();
        if (state == null) {
            r.drawText("(no scenes)", x + 10, r.baselineForBox(y, h), Theme.toArgb(theme.textMuted));
            return;
        }

        ScriptEditorDialog scriptDialog = runtime.scriptEditorDialog();
        if (scriptDialog != null && scriptDialog.isOpen()) {
            scriptDialog.cancelInteractions(uiContext);
        }

        var input = interactive ? ctx.ui().input() : null;
        boolean canInteract = input != null;
        float mx = canInteract ? input.mousePos().x : -1;
        float my = canInteract ? input.mousePos().y : -1;

        boolean hoveringTabBar = canInteract && mx >= x && my >= y && mx < x + w && my < y + h;
        if (uiContext != null && canInteract) {
            uiContext.dragDrop().registerDropTarget(new DropTarget<String>(
                    0x5C9121,
                    EditorDnD.TYPE_SCENE_ID,
                    x,
                    y,
                    w,
                    h,
                    (String sceneId, float dropX, float dropY) -> {
                        if (sceneId == null || sceneId.isBlank()) {
                            return;
                        }
                        state.ensureSceneOpen(sceneId);
                        selectScene(sceneId);
                    }
            ));

            if (hoveringTabBar
                    && uiContext.dragDrop().hoveredTarget() != null
                    && uiContext.dragDrop().hoveredTarget().id() == 0x5C9121) {
                int hl = Theme.mulAlpha(Theme.toArgb(theme.accent), 0.10f);
                r.drawRect(x, y, w, h, hl);
            }
        }

        syncAllTabs(state);
        int before = sceneTabs.activeIndex();
        sceneTabs.render(r, uiContext, input, theme, x, y, w, h, true);
        syncTabOrderFromBar(state);
        int after = sceneTabs.activeIndex();
        if (after != before) {
            var tabs = sceneTabs.tabs();
            EditorContext editorCtx = EditorOverlayBus.get();

            if (editorCtx != null && before >= 0 && before < tabs.size()) {
                Object prevData = tabs.get(before).userData;
                if (prevData instanceof SceneTabData prevSd) {
                    var cam = editorCtx.camera();
                    state.sceneCameraStates.put(prevSd.sceneId(), new double[]{
                            cam.getPos().x, cam.getPos().y, cam.getPos().z,
                            cam.getYaw(), cam.getPitch()
                    });
                }
            }

            if (after >= 0 && after < tabs.size()) {
                Object data = tabs.get(after).userData;
                if (data instanceof SceneTabData sd) {
                    if (editorCtx != null) {
                        double[] saved = state.sceneCameraStates.get(sd.sceneId());
                        if (saved != null) {
                            editorCtx.camera().setCamera(
                                    new Vec3d(saved[0], saved[1], saved[2]),
                                    saved[3], saved[4]);
                        }
                    }
                    state.activeScriptPath = "";
                    state.activeTextAssetPath = "";
                    selectScene(sd.sceneId());
                } else if (data instanceof ScriptTabData sd) {
                    state.activeScriptPath = sd.path();
                    state.activeTextAssetPath = "";
                    ScriptEditorDialog dialog = runtime.scriptEditorDialog();
                    if (dialog != null && (!dialog.isOpen() || !sd.path().equals(dialog.scriptPath()))) {
                        dialog.open(0, sd.path());
                    }
                } else if (data instanceof TextAssetTabData td) {
                    state.activeTextAssetPath = td.path();
                    state.activeScriptPath = "";
                    var dialog = runtime.textAssetEditorDialog();
                    if (dialog != null && (!dialog.isOpen() || !td.path().equals(dialog.resPath()))) {
                        dialog.open(td.path(), null);
                    }
                }
            }
        }
    }

    private void syncAllTabs(EditorState state) {
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
            tab.userData = new SceneTabData(sceneId);
            tab.closable = !"main".equals(sceneId);
            tab.pinned = "main".equals(sceneId);
            tabs.add(tab);
        }

        for (String path : state.openScriptPaths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            TabBar.Tab tab = new TabBar.Tab(scriptTabLabel(path));
            tab.userData = new ScriptTabData(path);
            tab.closable = true;
            tabs.add(tab);
        }

        for (String path : state.openTextAssetPaths) {
            if (path == null || path.isBlank()) continue;
            TabBar.Tab tab = new TabBar.Tab(textAssetTabLabel(path));
            tab.userData = new TextAssetTabData(path);
            tab.closable = true;
            tabs.add(tab);
        }

        int activeIndex = -1;

        String activeTextAsset = state.activeTextAssetPath;
        if (activeTextAsset != null && !activeTextAsset.isBlank()) {
            for (int i = 0; i < tabs.size(); i++) {
                if (tabs.get(i).userData instanceof TextAssetTabData td && activeTextAsset.equals(td.path())) {
                    activeIndex = i;
                    break;
                }
            }
        }

        String activeScript = state.activeScriptPath;
        if (activeScript != null && !activeScript.isBlank()) {
            for (int i = 0; i < tabs.size(); i++) {
                if (tabs.get(i).userData instanceof ScriptTabData sd && activeScript.equals(sd.path())) {
                    activeIndex = i;
                    break;
                }
            }
        }
        if (activeIndex < 0 && state.activeSceneId != null && !state.activeSceneId.isBlank()) {
            for (int i = 0; i < tabs.size(); i++) {
                if (tabs.get(i).userData instanceof SceneTabData sd && state.activeSceneId.equals(sd.sceneId())) {
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

    private void syncTabOrderFromBar(EditorState state) {
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

        ArrayList<String> nextScenes = new ArrayList<>();
        ArrayList<String> nextScripts = new ArrayList<>();
        ArrayList<String> nextTextAssets = new ArrayList<>();
        for (TabBar.Tab tab : tabs) {
            if (tab == null) continue;
            if (tab.userData instanceof SceneTabData sd && !sd.sceneId().isBlank()) {
                if (!nextScenes.contains(sd.sceneId())) nextScenes.add(sd.sceneId());
            } else if (tab.userData instanceof ScriptTabData sd && !sd.path().isBlank()) {
                if (!nextScripts.contains(sd.path())) nextScripts.add(sd.path());
            } else if (tab.userData instanceof TextAssetTabData td && !td.path().isBlank()) {
                if (!nextTextAssets.contains(td.path())) nextTextAssets.add(td.path());
            }
        }
        if (!nextScenes.contains("main")) {
            nextScenes.add(0, "main");
        }
        if (!nextScenes.equals(state.openSceneIds)) {
            state.openSceneIds.clear();
            state.openSceneIds.addAll(nextScenes);
        }
        if (!nextScripts.equals(state.openScriptPaths)) {
            state.openScriptPaths.clear();
            state.openScriptPaths.addAll(nextScripts);
        }
        if (!nextTextAssets.equals(state.openTextAssetPaths)) {
            state.openTextAssetPaths.clear();
            state.openTextAssetPaths.addAll(nextTextAssets);
        }
    }

    private void closeTab(int index, TabBar.Tab tab) {
        if (tab == null || tab.userData == null) {
            return;
        }
        if (tab.userData instanceof SceneTabData sd) {
            closeSceneTabData(index, sd.sceneId());
        } else if (tab.userData instanceof ScriptTabData sd) {
            EditorState state = runtime.state();
            if (state != null) {
                state.closeScriptTab(sd.path());
                ScriptEditorDialog dialog = runtime.scriptEditorDialog();
                if (dialog != null && state.openScriptPaths.isEmpty()) {
                    dialog.close();
                }
            }
        } else if (tab.userData instanceof TextAssetTabData td) {
            EditorState state = runtime.state();
            if (state != null) {
                state.closeTextAssetTab(td.path());
                var dialog = runtime.textAssetEditorDialog();
                if (dialog != null && state.openTextAssetPaths.isEmpty()) {
                    dialog.close();
                }
            }
        }
    }

    private void closeSceneTabData(int index, String sceneId) {
        if (sceneId == null || sceneId.isBlank() || "main".equals(sceneId)) {
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

    private static String scriptTabLabel(String path) {
        if (path == null || path.isBlank()) {
            return "Script";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String textAssetTabLabel(String path) {
        if (path == null || path.isBlank()) return "Text";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
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

}
