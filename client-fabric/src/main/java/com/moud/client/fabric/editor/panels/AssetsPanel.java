package com.moud.client.fabric.editor.panels;


import com.miry.ui.PanelContext;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.clipboard.Clipboard;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.event.TextInputEvent;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.theme.Icon;
import com.miry.ui.widgets.AssetBrowser;
import com.miry.ui.widgets.ContextMenu;
import com.miry.ui.widgets.StripTabs;
import com.miry.ui.widgets.TextField;
import com.moud.client.fabric.assets.AssetsClient;
import com.moud.client.fabric.editor.util.AssetImportUtil;
import com.moud.client.fabric.editor.util.EditorUiUtil;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.core.assets.AssetType;
import com.moud.net.protocol.AssetManifestResponse;
import com.moud.net.protocol.AssetTransferStatus;
import com.moud.net.protocol.AssetUploadAck;
import com.moud.net.protocol.SceneInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

public final class AssetsPanel extends Panel implements AssetsClient.Listener {
    private final EditorRuntime runtime;

    private final StripTabs dockTabs = new StripTabs();
    private final StripTabs.Style dockTabStyle = new StripTabs.Style();

    private final TextField filterField = new TextField();
    private final TextField createSceneIdField = new TextField();
    private final TextField createSceneNameField = new TextField();
    private final AssetBrowser<AssetManifestResponse.Entry> browser = new AssetBrowser<>();
    private final AssetBrowser.Style browserStyle = new AssetBrowser.Style();
    private final ArrayList<AssetManifestResponse.Entry> entries = new ArrayList<>();
    private final ContextMenu sceneMenu = new ContextMenu();
    private final ContextMenu assetContextMenu = new ContextMenu();
    private boolean requestedOnce;
    private String lastFilter = "";
    private int activeDockTab;
    private boolean createSceneOpen;
    private boolean createSceneFocusRequested;
    private String createSceneError;
    private String deleteConfirmSceneId;
    private long deleteConfirmUntilMs;
    private String sceneMenuSceneId;
    private AssetManifestResponse.Entry lastSelectedAsset;
    private long lastAssetClickTime;

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

    public void openCreateScene() {
        activeDockTab = 1;
        createSceneOpen = true;
        createSceneFocusRequested = true;
        createSceneError = null;
        sceneMenu.close();
        assetContextMenu.close();
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
    public void onUploadAck(AssetUploadAck ack) {
        if (ack == null || runtime == null) {
            return;
        }
        boolean done = ack.status() == AssetTransferStatus.ALREADY_PRESENT
                || (ack.status() == AssetTransferStatus.OK && "stored".equalsIgnoreCase(ack.message()));
        if (!done) {
            return;
        }
        AssetsClient assets = runtime.assets();
        if (assets != null) {
            assets.requestManifest(runtime.session());
        }
    }

    @Override
    public void render(PanelContext ctx) {
        Ui ui = ctx.ui();
        UiRenderer r = ctx.renderer();
        Theme theme = ui.theme();
        UiContext uiContext = ctx.uiContext();
        boolean interactive = runtime != null && !runtime.uiBlocked();
        var input = interactive ? ui.input() : null;

        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        int h = ctx.height();

        ui.beginPanel(x, y, w, h);

        int tabH = 26;
        int toolbarH = 30;
        int pathH = 24;
        int cursorY = y;

        renderDockTabs(ui, r, uiContext, theme, x, cursorY, w, tabH, interactive);
        cursorY += tabH;

        renderToolbar(ui, r, uiContext, theme, x, cursorY, w, toolbarH, interactive);
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
            browser.render(r, input, theme, browserX, browserY, browserW, browserH);

            handleAssetInteraction(ui, r);
        } else {
            int listX = x;
            int listY = cursorY;
            int listW = w;
            int listH = Math.max(0, y + h - listY);
            renderScenesList(ui, r, uiContext, theme, listX, listY, listW, listH, interactive);
        }

        ui.endPanel();
    }

    public void handleTextInput(UiContext ctx, TextInputEvent e) {
        if (createSceneIdField.isFocused(ctx)) {
            createSceneIdField.handleTextInput(e);
        } else if (createSceneNameField.isFocused(ctx)) {
            createSceneNameField.handleTextInput(e);
        } else if (filterField.isFocused(ctx)) {
            filterField.handleTextInput(e);
        }
    }

    public void handleKey(UiContext ctx, KeyEvent e) {
        Clipboard clipboard = ctx != null ? ctx.clipboard() : null;
        if (createSceneIdField.isFocused(ctx)) {
            createSceneIdField.handleKey(e, clipboard);
        } else if (createSceneNameField.isFocused(ctx)) {
            createSceneNameField.handleKey(e, clipboard);
        } else if (filterField.isFocused(ctx)) {
            filterField.handleKey(e, clipboard);
        }
    }

    private void renderDockTabs(Ui ui, UiRenderer r, UiContext uiContext, Theme theme, int x, int y, int w, int h, boolean interactive) {
        var input = interactive ? ui.input() : null;
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

    private void renderToolbar(Ui ui, UiRenderer r, UiContext uiContext, Theme theme, int x, int y, int w, int h, boolean interactive) {
        var input = interactive ? ui.input() : null;
        int bg = Theme.toArgb(theme.windowBg);
        r.drawRect(x, y, w, h, bg);
        r.drawRect(x, y + h - 1, w, 1, Theme.toArgb(theme.headerLine));

        int pad = theme.design.space_sm;
        int searchH = 22;
        int rightW = activeDockTab == 0 ? (24 * 2 + pad) : (52 * 3 + pad * 2);
        int searchW = Math.max(120, w - pad * 3 - rightW);
        int searchX = x + pad;
        int searchY = y + (h - searchH) / 2;

        filterField.render(r, uiContext, input, theme, searchX, searchY, searchW, searchH, true);
        if ((filterField.text() == null || filterField.text().isEmpty()) && (uiContext == null || !filterField.isFocused(uiContext))) {
            int hint = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.70f);
            float iconSize = Math.min(theme.design.icon_sm, searchH - 6);
            theme.icons.draw(r, Icon.SEARCH, searchX + 6, searchY + (searchH - iconSize) * 0.5f, iconSize, hint);
            r.drawText(activeDockTab == 0 ? "Filter Files" : "Filter Scenes",
                    searchX + 6 + iconSize + 6, r.baselineForBox(searchY, searchH), hint);
        }

        int btnY = y + (h - 24) / 2;
        if (activeDockTab == 0) {
            int refreshX = x + w - pad - 24;
            int uploadX = refreshX - pad - 24;
            renderIconButton(ui, r, theme, uploadX, btnY, 24, 24, Icon.ADD, interactive, () -> AssetImportUtil.importAssetFile(runtime));
            renderIconButton(ui, r, theme, refreshX, btnY, 24, 24, Icon.SNAP, interactive, () -> {
                AssetsClient assets = runtime.assets();
                if (assets != null) {
                    assets.requestManifest(runtime.session());
                }
            });
            return;
        }

        EditorState state = runtime.state();
        String activeSceneId = state != null ? state.activeSceneId : null;
        boolean canDelete = activeSceneId != null && !activeSceneId.isBlank() && !"main".equals(activeSceneId);
        long now = System.currentTimeMillis();
        boolean deleteConfirm = canDelete
                && activeSceneId.equals(deleteConfirmSceneId)
                && now < deleteConfirmUntilMs;
        String delLabel = deleteConfirm ? "Confirm" : "Del";

        int delW = 52;
        int impW = 52;
        int newW = 52;
        int delX = x + w - pad - delW;
        int impX = delX - pad - impW;
        int newX = impX - pad - newW;

        renderTextButton(ui, r, theme, newX, btnY, newW, 24, "New", interactive, () -> {
            createSceneOpen = !createSceneOpen;
            createSceneError = null;
            if (createSceneOpen) {
                createSceneFocusRequested = true;
            }
        });

        renderTextButton(ui, r, theme, impX, btnY, impW, 24, "Import", interactive, () -> {
            AssetImportUtil.importSceneFile(runtime);
        });

        renderTextButton(ui, r, theme, delX, btnY, delW, 24, delLabel, interactive && canDelete, () -> {
            if (!canDelete) {
                return;
            }
            if (!deleteConfirm) {
                deleteConfirmSceneId = activeSceneId;
                deleteConfirmUntilMs = System.currentTimeMillis() + 3000L;
                return;
            }
            deleteConfirmSceneId = null;
            deleteConfirmUntilMs = 0L;
            runtime.net().deleteScene(runtime.session(), activeSceneId);
            EditorState st = runtime.state();
            if (st != null) {
                st.pendingSnapshot = true;
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

    private void renderScenesList(Ui ui, UiRenderer r, UiContext uiContext, Theme theme, int x, int y, int w, int h, boolean interactive) {
        int bg = Theme.toArgb(theme.panelBg);
        r.drawRect(x, y, w, h, bg);

        EditorState state = runtime.state();
        int pad = theme.design.space_sm;
        int cursorY = y + pad;

        var input = interactive ? ui.input() : null;
        boolean canInteract = input != null;
        float mx = canInteract ? input.mousePos().x : -1;
        float my = canInteract ? input.mousePos().y : -1;
        boolean click = canInteract && input.mouseReleased();

        String filter = filterField.text() == null ? "" : filterField.text().trim().toLowerCase(Locale.ROOT);

        if (createSceneOpen) {
            int rowH = 30;
            int fieldH = 22;
            int fieldY = cursorY + (rowH - fieldH) / 2;
            int btnW = 70;
            int cancelW = 70;
            int cancelX = x + w - pad - cancelW;
            int createX = cancelX - pad - btnW;

            int idW = 120;
            int idX = x + pad;
            int nameX = idX + idW + pad;
            int nameW = Math.max(60, createX - pad - nameX);

            createSceneIdField.render(r, uiContext, input, theme, idX, fieldY, idW, fieldH, true);
            if ((createSceneIdField.text() == null || createSceneIdField.text().isEmpty()) && (uiContext == null || !createSceneIdField.isFocused(uiContext))) {
                r.drawText("scene_id", idX + 6, r.baselineForBox(fieldY, fieldH), Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.65f));
            }
            createSceneNameField.render(r, uiContext, input, theme, nameX, fieldY, nameW, fieldH, true);
            if ((createSceneNameField.text() == null || createSceneNameField.text().isEmpty()) && (uiContext == null || !createSceneNameField.isFocused(uiContext))) {
                r.drawText("Display name (optional)", nameX + 6, r.baselineForBox(fieldY, fieldH), Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.65f));
            }

            renderTextButton(ui, r, theme, createX, fieldY, btnW, fieldH, "Create", interactive, () -> {
                String sid = normalizeSceneId(createSceneIdField.text());
                if (!isValidSceneId(sid)) {
                    createSceneError = "Invalid id (use [a-z0-9_-], max 64 chars)";
                    return;
                }
                String dn = createSceneNameField.text();
                runtime.net().createScene(runtime.session(), sid, dn);
                if (state != null) {
                    state.pendingSnapshot = true;
                }
                createSceneOpen = false;
                createSceneError = null;
                createSceneIdField.setText("");
                createSceneNameField.setText("");
            });

            renderTextButton(ui, r, theme, cancelX, fieldY, cancelW, fieldH, "Cancel", interactive, () -> {
                createSceneOpen = false;
                createSceneError = null;
            });

            if (interactive && createSceneFocusRequested && uiContext != null) {
                createSceneFocusRequested = false;
                createSceneIdField.focus(uiContext);
            }

            cursorY += rowH + pad;
            if (createSceneError != null && !createSceneError.isBlank()) {
                r.drawText(createSceneError, x + pad, r.baselineForBox(cursorY, 18), Theme.toArgb(theme.danger));
                cursorY += 18 + pad;
            }
        }

        if (state == null || state.scenes == null || state.scenes.isEmpty()) {
            r.drawText("(no scenes)", x + 12, r.baselineForBox(cursorY, 24), Theme.toArgb(theme.textMuted));
            return;
        }

        int rowH = Math.max(24, Math.round(theme.design.widget_height_md));

        ArrayList<SceneInfo> filtered = new ArrayList<>(state.scenes.size());
        for (SceneInfo scene : state.scenes) {
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
            filtered.add(scene);
        }

        int listY = cursorY;
        int listH = Math.max(0, y + h - listY);
        if (filtered.isEmpty()) {
            r.drawText("(no matches)", x + 12, r.baselineForBox(listY, 24), Theme.toArgb(theme.textMuted));
            return;
        }
        if (listH <= 0) {
            return;
        }

        int contentHeight = filtered.size() * rowH;
        Ui.ScrollArea area = ui.beginScrollArea(r, "assetsScenesScroll", x, listY, w, listH, contentHeight);
        int scrollY = (int) area.scrollY();

        int first = Math.max(0, scrollY / Math.max(1, rowH));
        int visible = Math.max(1, (listH / Math.max(1, rowH)) + 2);
        int last = Math.min(filtered.size(), first + visible);

        boolean rightPressed = interactive && runtime.rightPressed();
        for (int i = first; i < last; i++) {
            SceneInfo scene = filtered.get(i);
            String sceneId = scene.sceneId();
            String filename = sceneId + ".moud.scene";
            String display = scene.uiLabel();
            if (display == null || display.isBlank()) {
                display = sceneId;
            }

            int rowY = listY + i * rowH - scrollY;
            boolean active = sceneId.equals(state.activeSceneId);
            boolean hovered = canInteract && mx >= x && my >= rowY && mx < x + w && my < rowY + rowH;
            if (active) {
                int fill = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.65f);
                r.drawRect(x, rowY, w, rowH, fill);
            } else if (hovered) {
                int fill = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.45f);
                r.drawRect(x, rowY, w, rowH, fill);
            }

            int textX = x + pad;
            float iconSize = Math.min(theme.design.icon_sm, rowH - 6);
            if (theme.icons != null) {
                theme.icons.draw(r, Icon.FILE, textX, rowY + (rowH - iconSize) * 0.5f, iconSize, Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.85f));
            }
            int fileX = textX + (int) Math.ceil(iconSize) + 8;
            int innerRight = x + w - pad;
            int idealSplit = x + Math.round(w * 0.60f);
            int minSplit = fileX + 140;
            int maxSplit = innerRight - 100;
            boolean twoCols = maxSplit > minSplit;
            int splitX = idealSplit;
            if (twoCols) {
                splitX = Math.max(minSplit, Math.min(maxSplit, splitX));
            }

            int fileMaxW = Math.max(0, (twoCols ? (splitX - fileX - pad) : (innerRight - fileX)));
            String fileText = ellipsize(r, filename, fileMaxW);
            r.drawText(fileText, fileX, r.baselineForBox(rowY, rowH), Theme.toArgb(theme.text));
            int muted = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.80f);
            if (twoCols) {
                int displayMaxW = Math.max(0, innerRight - splitX);
                if (displayMaxW > 24) {
                    r.drawText(ellipsize(r, display, displayMaxW), splitX, r.baselineForBox(rowY, rowH), muted);
                }
            }

            if (hovered && canInteract && input.mousePressed()) {
                runtime.beginSceneDrag(sceneId, mx, my);
            }

            if (hovered && click) {
                if (!runtime.sceneDragActive()) {
                    state.ensureSceneOpen(sceneId);
                    runtime.net().selectScene(runtime.session(), state, sceneId);
                }
            }

            if (hovered && rightPressed && !sceneMenu.isOpen()) {
                sceneMenuSceneId = sceneId;
                openSceneMenu(state, sceneId);
                EditorUiUtil.openMenuClamped(sceneMenu, runtime, (int) mx, (int) my);
            }
        }

        ui.endScrollArea(area);

        if (sceneMenu.isOpen()) {
            int itemH = Math.max(18, theme.tokens.itemHeight);
            if (input != null) {
                sceneMenu.updateFromInput(input, theme, itemH);
                EditorUiUtil.clampOpenMenuToScreen(sceneMenu, runtime);
            }
            sceneMenu.render(r, theme, itemH,
                    Theme.toArgb(theme.panelBg),
                    Theme.toArgb(theme.widgetHover),
                    Theme.toArgb(theme.text),
                    sceneMenu.hoverIndex());
            if (canInteract && input.mousePressed()) {
                sceneMenu.handleClick((int) mx, (int) my, itemH);
            }
        }
    }

    private void openSceneMenu(EditorState state, String sceneId) {
        sceneMenu.clear();
        sceneMenu.addItem("Open as Scene", () -> {
            EditorState st = state != null ? state : runtime.state();
            if (st != null) {
                st.ensureSceneOpen(sceneId);
            }
            runtime.net().selectScene(runtime.session(), st, sceneId);
        });

        if (state != null && sceneId != null && !"main".equals(sceneId) && state.openSceneIds.contains(sceneId) && state.openSceneIds.size() > 1) {
            sceneMenu.addItem("Close Tab", () -> {
                state.openSceneIds.removeIf(id -> sceneId.equals(id));
                if (sceneId.equals(state.activeSceneId)) {
                    String next = state.openSceneIds.isEmpty() ? "main" : state.openSceneIds.get(state.openSceneIds.size() - 1);
                    state.ensureSceneOpen(next);
                    runtime.net().selectScene(runtime.session(), state, next);
                }
            });
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
            boolean isScene = name != null && name.endsWith(".moud.scene");
            var item = new AssetBrowser.AssetItem<>(entry, name);
            item.icon = isScene ? Icon.FILE : iconFor(entry.meta().type());
            item.type = isScene ? "Scene" : entry.meta().type().name();
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

    private static void renderTextButton(Ui ui,
                                         UiRenderer r,
                                         Theme theme,
                                         int x,
                                         int y,
                                         int w,
                                         int h,
                                         String label,
                                         boolean interactive,
                                         Runnable action) {
        var input = ui.input();
        boolean canInteract = interactive && input != null;
        float mx = canInteract ? input.mousePos().x : -1;
        float my = canInteract ? input.mousePos().y : -1;
        boolean hovered = canInteract && mx >= x && my >= y && mx < x + w && my < y + h;

        int fill = Theme.mulAlpha(Theme.toArgb(theme.headerBg), 0.90f);
        if (hovered) {
            fill = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.75f);
        }
        r.drawRoundedRect(x, y, w, h, theme.design.radius_sm, fill);
        int text = interactive ? Theme.toArgb(theme.text) : Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.60f);
        r.drawText(label == null ? "" : label, x + theme.design.space_sm, r.baselineForBox(y, h), text);

        if (hovered && canInteract && input.mouseReleased() && action != null) {
            action.run();
        }
    }

    private static String normalizeSceneId(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String ellipsize(UiRenderer r, String text, int maxWidth) {
        if (text == null || text.isBlank() || r == null) {
            return text == null ? "" : text;
        }
        if (maxWidth <= 0) {
            return "";
        }
        if (r.measureText(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        if (r.measureText(ellipsis) > maxWidth) {
            return "";
        }
        int lo = 0;
        int hi = text.length();
        int best = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            String candidate = text.substring(0, mid) + ellipsis;
            if (r.measureText(candidate) <= maxWidth) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return text.substring(0, best) + ellipsis;
    }

    private static boolean isValidSceneId(String sceneId) {
        if (sceneId == null) {
            return false;
        }
        String id = sceneId.trim();
        if (id.isEmpty() || id.length() > 64) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '_' || c == '-') {
                continue;
            }
            if (c >= 'a' && c <= 'z') {
                continue;
            }
            if (c >= '0' && c <= '9') {
                continue;
            }
            return false;
        }
        return true;
    }

    private void handleAssetInteraction(Ui ui, UiRenderer r) {
        if (runtime != null && runtime.uiBlocked()) {
            return;
        }
        var selected = browser.selectedItem();
        if (selected == null || selected.data == null) {
            return;
        }

        var input = ui.input();
        if (input == null) {
            return;
        }

        String path = selected.data.path() != null ? selected.data.path().value() : null;
        if (path == null || !path.endsWith(".moud.scene")) {
            return;
        }

        if (input.mousePressed() && runtime.sceneDragId() == null) {
            String filename = path.substring(path.lastIndexOf('/') + 1);
            String sceneId = filename.substring(0, filename.length() - ".moud.scene".length());
            if (!sceneId.isBlank()) {
                runtime.beginSceneDrag(sceneId, input.mousePos().x, input.mousePos().y);
            }
        }

        boolean clicked = input.mouseReleased();
        if (clicked) {
            long now = System.currentTimeMillis();
            boolean isDoubleClick = selected.data.equals(lastSelectedAsset) && (now - lastAssetClickTime) < 300;
            lastSelectedAsset = selected.data;
            lastAssetClickTime = now;

            if (isDoubleClick) {
                openSceneFromAsset(path);
            }
        }

        boolean rightPressed = runtime.rightPressed();
        if (rightPressed && !assetContextMenu.isOpen()) {
            float mx = input.mousePos().x;
            float my = input.mousePos().y;
            openAssetContextMenu(path);
            EditorUiUtil.openMenuClamped(assetContextMenu, runtime, (int) mx, (int) my);
        }

        if (assetContextMenu.isOpen()) {
            int itemH = Math.max(18, ui.theme().tokens.itemHeight);
            assetContextMenu.updateFromInput(input, ui.theme(), itemH);
            EditorUiUtil.clampOpenMenuToScreen(assetContextMenu, runtime);
            assetContextMenu.render(r, ui.theme(), itemH,
                    Theme.toArgb(ui.theme().panelBg),
                    Theme.toArgb(ui.theme().widgetHover),
                    Theme.toArgb(ui.theme().text),
                    assetContextMenu.hoverIndex());
            if (input.mousePressed()) {
                assetContextMenu.handleClick((int) input.mousePos().x, (int) input.mousePos().y, itemH);
            }
        }
    }

    private void openSceneFromAsset(String path) {
        String filename = path.substring(path.lastIndexOf('/') + 1);
        if (!filename.endsWith(".moud.scene")) {
            return;
        }
        String sceneId = filename.substring(0, filename.length() - ".moud.scene".length());
        EditorState state = runtime.state();
        if (state != null) {
            state.ensureSceneOpen(sceneId);
            runtime.net().selectScene(runtime.session(), state, sceneId);
        }
    }

    private void openAssetContextMenu(String path) {
        assetContextMenu.clear();
        assetContextMenu.addItem("Open Scene", () -> openSceneFromAsset(path));
    }

    // Imports handled by AssetImportUtil.
}
