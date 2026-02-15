package com.moud.client.fabric.editor.panels;

import com.miry.ui.PanelContext;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.AssetBrowser;
import com.miry.ui.widgets.StripTabs;
import com.miry.ui.widgets.TextField;
import com.moud.client.fabric.assets.AssetsClient;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.core.assets.AssetType;
import com.moud.net.protocol.AssetManifestResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class AssetsPanel extends Panel implements AssetsClient.Listener {
    private final EditorRuntime runtime;

    private final StripTabs dockTabs = new StripTabs();
    private final StripTabs.Style dockTabStyle = new StripTabs.Style();

    private final TextField filterField = new TextField();
    private final AssetBrowser<AssetManifestResponse.Entry> browser = new AssetBrowser<>();
    private final AssetBrowser.Style browserStyle = new AssetBrowser.Style();
    private final ArrayList<AssetManifestResponse.Entry> entries = new ArrayList<>();
    private boolean requestedOnce;
    private String lastFilter = "";
    private int activeDockTab;

    public AssetsPanel(EditorRuntime runtime) {
        super("");
        this.runtime = runtime;
        AssetsClient assets = runtime.assets();
        if (assets != null) {
            assets.addListener(this);
        }
        browser.setViewMode(AssetBrowser.ViewMode.LIST);
        browserStyle.drawContainer = false;
        browserStyle.textColor = 0xFFE0E0E0;
        browserStyle.mutedColor = 0xFFB3B3B3;
        browserStyle.rowHoverBg = 0xFF404553;
        browserStyle.rowSelectedBg = 0xFF3D5E89;
        browser.setStyle(browserStyle);
    }

    @Override
    public void onManifest(AssetManifestResponse response) {
        entries.clear();
        if (response != null && response.entries() != null) {
            entries.addAll(response.entries());
        }
        entries.sort(Comparator.comparing(e -> e.path() == null ? "" : e.path().value()));
        rebuildBrowser(filterField.text());
    }

    @Override
    public void render(PanelContext ctx) {
        Ui ui = ctx.ui();
        UiRenderer r = ctx.renderer();
        Theme theme = ui.theme();
        UiContext uiContext = ctx.uiContext();

        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        int h = ctx.height();

        ui.beginPanel(x, y, w, h);

        int tabH = 26;
        int toolbarH = 30;
        int pathH = 24;
        int cursorY = y;

        renderDockTabs(ui, r, uiContext, theme, x, cursorY, w, tabH);
        cursorY += tabH;

        renderToolbar(ui, r, uiContext, theme, x, cursorY, w, toolbarH);
        cursorY += toolbarH;

        renderPathBar(r, theme, x, cursorY, w, pathH, activeDockTab == 0 ? "res://" : "scenes/");
        cursorY += pathH;

        if (activeDockTab == 0) {
            AssetsClient assets = runtime.assets();
            if (assets != null && !requestedOnce) {
                requestedOnce = true;
                assets.requestManifest(runtime.session());
            }

            String filter = filterField.text() == null ? "" : filterField.text();
            if (!filter.equals(lastFilter)) {
                lastFilter = filter;
                rebuildBrowser(filter);
            }

            int browserX = x;
            int browserY = cursorY;
            int browserW = w;
            int browserH = Math.max(0, y + h - browserY);
            browser.render(r, ui.input(), theme, browserX, browserY, browserW, browserH);
        } else {
            int listX = x;
            int listY = cursorY;
            int listW = w;
            int listH = Math.max(0, y + h - listY);
            renderScenesList(ui, r, theme, listX, listY, listW, listH);
        }

        ui.endPanel();
    }

    private void renderDockTabs(Ui ui, UiRenderer r, UiContext uiContext, Theme theme, int x, int y, int w, int h) {
        var input = ui.input();
        dockTabStyle.containerBg = Theme.toArgb(theme.headerLine);
        dockTabStyle.tabActiveBg = Theme.toArgb(theme.windowBg);
        dockTabStyle.tabInactiveBg = Theme.toArgb(theme.headerBg);
        dockTabStyle.tabHoverBg = Theme.toArgb(theme.widgetHover);
        dockTabStyle.borderColor = Theme.toArgb(theme.headerLine);
        dockTabStyle.highlightColor = Theme.toArgb(theme.accent);
        dockTabStyle.textActive = Theme.toArgb(theme.text);
        dockTabStyle.textInactive = Theme.toArgb(theme.textMuted);
        dockTabStyle.equalWidth = true;
        dockTabStyle.highlightTop = true;
        dockTabStyle.highlightThickness = 2;

        String[] labels = new String[]{"FileSystem", "Scenes"};
        activeDockTab = dockTabs.render(r, uiContext, input, theme, x, y, w, h, labels, activeDockTab, true, dockTabStyle);
    }

    private void renderToolbar(Ui ui, UiRenderer r, UiContext uiContext, Theme theme, int x, int y, int w, int h) {
        var input = ui.input();
        int bg = Theme.toArgb(theme.windowBg);
        r.drawRect(x, y, w, h, bg);
        r.drawRect(x, y + h - 1, w, 1, Theme.toArgb(theme.headerLine));

        int pad = theme.design.space_sm;
        int searchH = 22;
        int searchW = Math.max(120, w - pad * 3 - 24);
        int searchX = x + pad;
        int searchY = y + (h - searchH) / 2;

        filterField.render(r, uiContext, input, theme, searchX, searchY, searchW, searchH, true);
        if ((filterField.text() == null || filterField.text().isEmpty()) && (uiContext == null || !filterField.isFocused(uiContext))) {
            int hint = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.70f);
            float iconSize = Math.min(theme.design.icon_sm, searchH - 6);
            theme.icons.draw(r, Icon.SEARCH, searchX + 6, searchY + (searchH - iconSize) * 0.5f, iconSize, hint);
            r.drawText("Filter Files", searchX + 6 + iconSize + 6, r.baselineForBox(searchY, searchH), hint);
        }

        int btnX = x + w - pad - 24;
        int btnY = y + (h - 24) / 2;
        renderIconButton(ui, r, theme, btnX, btnY, 24, 24, Icon.SNAP, true, () -> {
            AssetsClient assets = runtime.assets();
            if (assets != null) {
                assets.requestManifest(runtime.session());
            }
        });
    }

    private static void renderPathBar(UiRenderer r, Theme theme, int x, int y, int w, int h, String label) {
        int bg = Theme.toArgb(theme.headerBg);
        r.drawRect(x, y, w, h, bg);
        r.drawRect(x, y + h - 1, w, 1, Theme.toArgb(theme.headerLine));

        int pad = theme.design.space_md;
        int muted = Theme.toArgb(theme.disabledFg);
        r.drawText(label == null ? "" : label, x + pad, r.baselineForBox(y, h), muted);
    }

    private void renderScenesList(Ui ui, UiRenderer r, Theme theme, int x, int y, int w, int h) {
        int bg = Theme.toArgb(theme.panelBg);
        r.drawRect(x, y, w, h, bg);

        EditorState state = runtime.state();
        if (state == null || state.scenes == null || state.scenes.isEmpty()) {
            r.drawText("(no scenes)", x + 12, r.baselineForBox(y + 8, 24), Theme.toArgb(theme.textMuted));
            return;
        }

        var input = ui.input();
        boolean canInteract = input != null;
        float mx = canInteract ? input.mousePos().x : -1;
        float my = canInteract ? input.mousePos().y : -1;
        boolean click = canInteract && input.mouseReleased();

        int pad = theme.design.space_sm;
        int rowH = Math.max(24, Math.round(theme.design.widget_height_md));
        int cursorY = y + pad;
        String filter = filterField.text() == null ? "" : filterField.text().trim().toLowerCase(Locale.ROOT);

        for (var scene : state.scenes) {
            if (scene == null) {
                continue;
            }
            String sceneId = scene.sceneId();
            if (sceneId == null || sceneId.isBlank()) {
                continue;
            }
            String filename = sceneId + ".moud.scene";
            String display = scene.uiLabel();
            if (display == null || display.isBlank()) {
                display = sceneId;
            }

            if (!filter.isEmpty()) {
                String hay = (filename + " " + display).toLowerCase(Locale.ROOT);
                if (!hay.contains(filter)) {
                    continue;
                }
            }

            if (cursorY + rowH > y + h) {
                break;
            }

            boolean active = sceneId.equals(state.activeSceneId);
            boolean hovered = canInteract && mx >= x && my >= cursorY && mx < x + w && my < cursorY + rowH;
            if (active) {
                int fill = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.65f);
                r.drawRect(x, cursorY, w, rowH, fill);
            } else if (hovered) {
                int fill = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.45f);
                r.drawRect(x, cursorY, w, rowH, fill);
            }

            int textX = x + pad;
            r.drawText(filename, textX, r.baselineForBox(cursorY, rowH), Theme.toArgb(theme.text));
            int muted = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.80f);
            r.drawText(display, textX + 180, r.baselineForBox(cursorY, rowH), muted);

            if (hovered && click) {
                runtime.net().selectScene(runtime.session(), state, sceneId);
            }
            cursorY += rowH;
        }
    }

    private void rebuildBrowser(String filter) {
        browser.clear();
        String f = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        for (AssetManifestResponse.Entry entry : entries) {
            if (entry == null || entry.path() == null || entry.meta() == null) {
                continue;
            }
            String name = entry.path().value();
            if (!f.isEmpty() && (name == null || !name.toLowerCase(Locale.ROOT).contains(f))) {
                continue;
            }
            var item = new AssetBrowser.AssetItem<>(entry, name);
            item.icon = iconFor(entry.meta().type());
            item.type = entry.meta().type().name();
            browser.addItem(item);
        }
    }

    private static Icon iconFor(AssetType type) {
        if (type == null) {
            return Icon.FILE;
        }
        return switch (type) {
            case TEXT -> Icon.TEXT;
            case IMAGE -> Icon.IMAGE;
            case MODEL -> Icon.CODE;
            case AUDIO -> Icon.FILE;
            case BINARY -> Icon.FILE;
        };
    }

    private static void renderIconButton(Ui ui,
                                         UiRenderer r,
                                         Theme theme,
                                         int x,
                                         int y,
                                         int w,
                                         int h,
                                         Icon icon,
                                         boolean interactive,
                                         Runnable action) {
        var input = ui.input();
        boolean canInteract = interactive && input != null;
        float mx = canInteract ? input.mousePos().x : -1;
        float my = canInteract ? input.mousePos().y : -1;
        boolean hovered = canInteract && mx >= x && my >= y && mx < x + w && my < y + h;

        if (hovered) {
            int fill = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.65f);
            r.drawRoundedRect(x, y, w, h, theme.design.radius_sm, fill);
        }
        float iconSize = Math.min(theme.design.icon_sm, h - 6);
        theme.icons.draw(r, icon, x + (w - iconSize) * 0.5f, y + (h - iconSize) * 0.5f, iconSize, Theme.toArgb(theme.text));

        if (hovered && canInteract && input.mouseReleased() && action != null) {
            action.run();
        }
    }
}
