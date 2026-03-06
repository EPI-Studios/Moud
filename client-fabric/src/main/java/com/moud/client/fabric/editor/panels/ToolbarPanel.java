package com.moud.client.fabric.editor.panels;


import java.util.ArrayList;
import java.util.List;

public final class ToolbarPanel extends Panel {
    private final EditorRuntime runtime;

    private final List<String> menuTitles = List.of("Scene");
    private final ArrayList<ContextMenu> menus = new ArrayList<>();
    private int openMenuIndex = -1;

    private boolean initialized;

    public ToolbarPanel(EditorRuntime runtime) {
        super("");
        this.runtime = runtime;
        for (int i = 0; i < menuTitles.size(); i++) {
            menus.add(new ContextMenu());
        }
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
        if (!interactive && hasOpenMenu()) {
            closeMenus();
        }
        renderTopBar(ctx, r, uiContext, theme, x, y, w, h, interactive);
    }

    private void initMenus() {
        for (ContextMenu menu : menus) {
            menu.clear();
        }

        ContextMenu sceneMenu = menus.getFirst();
        sceneMenu.addItem("New Scene…", () -> runtime.openCreateScene());
        sceneMenu.addItem("Save Scene", () -> {
            boolean ok = runtime.saveCurrentScene();
            runtime.requestToast(ok ? "Saving scene…" : "Save failed: not connected", !ok, ok ? 2000 : 3500);
        });
        sceneMenu.addSeparator();
        sceneMenu.addItem("Request Snapshot", () -> runtime.net().requestSnapshot(runtime.session(), runtime.state()));
    }

    private void renderTopBar(PanelContext ctx,
                              UiRenderer r,
                              UiContext uiContext,
                              Theme theme,
                              int x,
                              int y,
                              int w,
                              int h,
                              boolean interactive) {
        var input = ctx.ui().input();
        int bg = Theme.toArgb(theme.windowBg);
        r.drawRect(x, y, w, h, bg);
        r.drawRect(x, y + h - 1, w, 1, Theme.toArgb(theme.headerLine));

        boolean canInteract = interactive && input != null;
        float mx = input != null ? input.mousePos().x : -1;
        float my = input != null ? input.mousePos().y : -1;

        int itemH = Math.max(18, h);
        int cursorX = x + 8;

        for (int i = 0; i < menuTitles.size(); i++) {
            String title = menuTitles.get(i);
            int itemW = Math.round(r.measureText(title)) + 16;
            int ix = cursorX;
            int iy = y + 2;
            int ih = itemH - 4;
            boolean hovered = canInteract && mx >= ix && my >= iy && mx < ix + itemW && my < iy + ih;
            boolean open = i == openMenuIndex && menus.get(i).isOpen();

            int fill = 0;
            if (open) {
                fill = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.75f);
            } else if (hovered) {
                fill = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.50f);
            }
            if (fill != 0) {
                r.drawRoundedRect(ix, iy, itemW, ih, theme.design.radius_sm, fill);
            }
            r.drawText(title, ix + 8, r.baselineForBox(iy, ih), Theme.toArgb(theme.text));

            if (hovered && canInteract && input.mousePressed()) {
                if (open) {
                    closeMenus();
                } else {
                    openMenu(i, ix, iy + ih);
                }
            } else if (hovered && canInteract && hasOpenMenu() && openMenuIndex != i) {
                openMenu(i, ix, iy + ih);
            }

            cursorX += itemW;
        }

        renderOpenMenu(ctx, r, theme, canInteract);

        if (canInteract && hasOpenMenu() && input.mousePressed()) {
            ContextMenu openMenu = menus.get(openMenuIndex);
            boolean insideMenu = mx >= openMenu.x()
                    && my >= openMenu.y()
                    && mx < openMenu.x() + Math.max(1, openMenu.lastWidth())
                    && my < openMenu.y() + Math.max(1, openMenu.lastHeight());
            if (!insideMenu) {
                closeMenus();
            }
        }
    }

    private void renderOpenMenu(PanelContext ctx, UiRenderer r, Theme theme, boolean interactive) {
        if (!interactive || openMenuIndex < 0 || openMenuIndex >= menus.size()) {
            return;
        }
        var input = ctx.ui().input();
        ContextMenu menu = menus.get(openMenuIndex);
        if (!menu.isOpen()) {
            return;
        }
        float mx = input.mousePos().x;
        float my = input.mousePos().y;
        int itemH = theme.tokens.itemHeight;
        menu.updateFromInput(input, theme, itemH);
        EditorUiUtil.clampOpenMenuToScreen(menu, runtime);
        menu.render(r, theme, itemH, Theme.toArgb(theme.panelBg), Theme.toArgb(theme.widgetHover), Theme.toArgb(theme.text), menu.hoverIndex());
        if (input.mousePressed()) {
            menu.handleClick((int) mx, (int) my, itemH);
        }
    }

    private boolean hasOpenMenu() {
        return openMenuIndex >= 0 && openMenuIndex < menus.size() && menus.get(openMenuIndex).isOpen();
    }

    private void openMenu(int index, int x, int y) {
        closeMenus();
        if (index < 0 || index >= menus.size()) {
            return;
        }
        openMenuIndex = index;
        EditorUiUtil.openMenuClamped(menus.get(index), runtime, x, y);
    }

    private void closeMenus() {
        openMenuIndex = -1;
        for (ContextMenu menu : menus) {
            menu.close();
        }
    }
}
