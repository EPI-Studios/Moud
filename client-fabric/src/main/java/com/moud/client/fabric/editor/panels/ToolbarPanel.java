package com.moud.client.fabric.editor.panels;

import com.miry.ui.PanelContext;
import com.miry.ui.UiContext;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.ContextMenu;
import com.miry.ui.widgets.MenuBar;
import com.moud.client.fabric.editor.state.EditorRuntime;

public final class ToolbarPanel extends Panel {
    private final EditorRuntime runtime;

    private final MenuBar menuBar = new MenuBar();
    private final ContextMenu sceneMenu = new ContextMenu();
    private final ContextMenu editorMenu = new ContextMenu();

    private boolean initialized;

    public ToolbarPanel(EditorRuntime runtime) {
        super("");
        this.runtime = runtime;
        menuBar.addMenu("Scene", sceneMenu);
        menuBar.addMenu("Editor", editorMenu);
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

        if (!initialized) {
            initialized = true;
            initMenus();
        }

        boolean interactive = runtime != null && !runtime.uiBlocked();
        if (!interactive && menuBar.hasOpenMenu()) {
            menuBar.closeAll();
        }

        var input = interactive ? ctx.ui().input() : null;
        menuBar.render(r, uiContext, input, theme, x, y, w, h, interactive);
    }

    private void initMenus() {
        sceneMenu.clear();
        sceneMenu.addItem("New Scene…", () -> runtime.openCreateScene());
        sceneMenu.addItem("Save Scene", () -> {
            boolean ok = runtime.saveCurrentScene();
            runtime.requestToast(ok ? "Saving scene…" : "Save failed: not connected", !ok, ok ? 2000 : 3500);
        });
        sceneMenu.addSeparator();
        sceneMenu.addItem("Request Snapshot", () -> runtime.net().requestSnapshot(runtime.session(), runtime.state()));

        editorMenu.clear();
        editorMenu.addItem("Settings…", runtime::openEditorSettings);
    }
}
