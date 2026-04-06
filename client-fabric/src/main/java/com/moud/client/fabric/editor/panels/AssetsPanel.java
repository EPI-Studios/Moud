package com.moud.client.fabric.editor.panels;


import com.miry.ui.PanelContext;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.clipboard.Clipboard;
import com.miry.ui.dnd.DragDropManager;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.event.TextInputEvent;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.theme.Icon;
import com.moud.client.fabric.render.MoudIcons;
import com.miry.ui.input.UiInput;
import com.miry.ui.widgets.ContextMenu;
import com.miry.ui.widgets.StripTabs;
import com.miry.ui.widgets.TextField;
import com.moud.client.fabric.assets.AssetsClient;
import com.moud.client.fabric.editor.util.AssetImportUtil;
import com.moud.client.fabric.editor.util.EditorDnD;
import com.moud.client.fabric.editor.util.EditorUiUtil;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.miry.graphics.Texture;
import com.moud.core.assets.AssetType;
import com.moud.net.protocol.AssetManifestResponse;
import com.moud.net.protocol.AssetTransferStatus;
import com.moud.net.protocol.AssetUploadAck;
import com.moud.net.protocol.SceneInfo;
import net.minecraft.client.MinecraftClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AssetsPanel extends Panel implements AssetsClient.Listener {
    private final EditorRuntime runtime;

    private final StripTabs dockTabs = new StripTabs();
    private final StripTabs.Style dockTabStyle = new StripTabs.Style();

    private final TextField filterField = new TextField();
    private final TextField createSceneIdField = new TextField();
    private final TextField createSceneNameField = new TextField();
    private final ArrayList<AssetManifestResponse.Entry> entries = new ArrayList<>();
    private final ContextMenu sceneMenu = new ContextMenu();
    private final ContextMenu assetContextMenu = new ContextMenu();

    private FolderNode fsRoot;
    private final Set<String> expandedFolders = new HashSet<>();
    private AssetManifestResponse.Entry selectedFsEntry;
    private AssetManifestResponse.Entry lastFsClickEntry;
    private long lastFsClickTime;

    private boolean gridView;

    private final Set<AssetManifestResponse.Entry> selectedEntries = new HashSet<>();
    private AssetManifestResponse.Entry lastShiftAnchor;
    private int sortMode;

    private String currentBrowsePath = "res://";

    private final ArrayList<AssetManifestResponse.Entry> flatVisibleFiles = new ArrayList<>();

    private boolean requestedOnce;
    private String lastFilter = "";
    private int activeDockTab;
    private boolean createSceneOpen;
    private boolean createSceneFocusRequested;
    private String createSceneError;
    private boolean createScene2D;
    private String deleteConfirmSceneId;
    private long deleteConfirmUntilMs;
    private String sceneMenuSceneId;

    public AssetsPanel(EditorRuntime runtime) {
        super("");
        this.runtime = runtime;
        AssetsClient assets = runtime.assets();
        if (assets != null) {
            assets.addListener(this);
        }
    }

    public void openCreateScene() {
        activeDockTab = 1;
        createSceneOpen = true;
        createSceneFocusRequested = true;
        createSceneError = null;
        createScene2D = false;
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
        rebuildFolderTree(filterField.text());
        EditorState state = runtime.state();
        if (state != null) {
            state.manifestEntries.clear();
            state.manifestEntries.addAll(entries);
        }
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

        int tabH = theme.design.tab_height_md;
        int toolbarH = Math.max(24, theme.design.toolbar_height);
        int pathH = 24;
        int cursorY = y;

        renderDockTabs(ui, r, uiContext, theme, x, cursorY, w, tabH, interactive);
        cursorY += tabH;

        renderToolbar(ui, r, uiContext, theme, x, cursorY, w, toolbarH, interactive);
        cursorY += toolbarH;

        if (activeDockTab == 0) {
            renderBreadcrumbs(ui, r, theme, input, x, cursorY, w, pathH, interactive);
        } else {
            renderPathBar(r, theme, x, cursorY, w, pathH, "scenes/");
        }
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
                rebuildFolderTree(filter);
            }

            int fsX = x;
            int fsY = cursorY;
            int fsW = w;
            int fsH = Math.max(0, y + h - fsY);

            if (gridView) {
                var input2 = interactive ? ui.input() : null;
                DragDropManager dnd = uiContext != null ? uiContext.dragDrop() : null;
                renderGridView(dnd, input2, r, theme, ui, fsX, fsY, fsW, fsH, interactive);
            } else {
                renderFileSystemTree(ui, r, uiContext, theme, input, fsX, fsY, fsW, fsH, interactive);
            }
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

    private void renderFileSystemTree(Ui ui, UiRenderer r, UiContext uiContext, Theme theme,
                                      UiInput input, int x, int y, int w, int h, boolean interactive) {
        r.drawRect(x, y, w, h, Theme.toArgb(theme.panelBg));
        if (fsRoot == null) {
            r.drawText("(no assets)", x + 12, r.baselineForBox(y, 24), Theme.toArgb(theme.textMuted));
            return;
        }

        boolean hasFilter = lastFilter != null && !lastFilter.isEmpty();

        int rowH = Math.max(20, theme.design.widget_height_md);
        int contentH;
        if (hasFilter) {
            contentH = countFlatFiles(fsRoot) * rowH;
        } else {
            contentH = countVisibleRows(fsRoot, true) * rowH;
        }

        Ui.ScrollArea area = ui.beginScrollArea(r, "assetsFsScroll", x, y, w, h, contentH);
        int scrollY = (int) area.scrollY();

        float mx = input != null ? input.mousePos().x : -1;
        float my = input != null ? input.mousePos().y : -1;
        boolean click = input != null && interactive && input.mouseReleased();
        boolean pressed = input != null && interactive && input.mousePressed();
        boolean rightPressed = interactive && runtime.rightPressed();
        DragDropManager dnd = uiContext != null ? uiContext.dragDrop() : null;

        int[] rowIndex = {0};
        if (hasFilter) {
            renderFlatFiltered(dnd, fsRoot, rowIndex, scrollY, x, y, w, h, rowH,
                    mx, my, click, pressed, rightPressed, r, theme, ui, input);
        } else {
            renderFolderNode(dnd, fsRoot, true, 0, rowIndex, scrollY, x, y, w, h, rowH,
                    mx, my, click, pressed, rightPressed, r, theme, ui, input);
        }

        ui.endScrollArea(area);
        drawScrollbar(r, theme, x, y, w, h, contentH, area.scrollY());

        renderAssetContextMenu(r, theme, input, pressed);
    }

    private void renderFolderNode(DragDropManager dnd, FolderNode node, boolean isRoot, int indent, int[] rowIndex,
                                  int scrollY, int panelX, int panelY, int panelW, int panelH,
                                  int rowH, float mx, float my, boolean click, boolean pressed,
                                  boolean rightPressed, UiRenderer r, Theme theme, Ui ui, UiInput input) {
        int pad = theme.design.space_sm;
        int indentPx = indent * 14;

        if (!isRoot) {
            int rowY = panelY + rowIndex[0] * rowH - scrollY;
            rowIndex[0]++;

            boolean expanded = expandedFolders.contains(node.fullPath);

            if (rowY + rowH > panelY && rowY < panelY + panelH) {
                boolean hovered = mx >= panelX && my >= rowY && mx < panelX + panelW && my < rowY + rowH;
                if (hovered) {
                    r.drawRect(panelX, rowY, panelW, rowH, Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.45f));
                }

                int textX = panelX + pad + indentPx;
                float arrowSize = Math.min(10f, rowH - 6f);
                float arrowX = textX;
                float arrowY = rowY + (rowH - arrowSize) * 0.5f;
                int arrowCol = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.80f);
                drawIcon(r, theme, "chevron_right", expanded ? Icon.CHEVRON_DOWN : Icon.CHEVRON_RIGHT, arrowX, arrowY, arrowSize, arrowCol);

                float folderIconSize = Math.min(14f, rowH - 4f);
                float folderX = textX + arrowSize + 3;
                int folderCol = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.90f);
                drawIcon(r, theme, expanded ? "folder_open" : "folder", Icon.FOLDER, folderX, rowY + (rowH - folderIconSize) * 0.5f, folderIconSize, folderCol);

                int labelX = (int) (folderX + folderIconSize + 4);
                int maxW = Math.max(0, panelX + panelW - pad - labelX);
                String label = ellipsize(r, node.name + "/", maxW);
                r.drawText(label, labelX, r.baselineForBox(rowY, rowH), Theme.toArgb(theme.text));

                if (hovered && click) {
                    if (expanded) {
                        expandedFolders.remove(node.fullPath);
                    } else {
                        expandedFolders.add(node.fullPath);
                        currentBrowsePath = node.fullPath;
                    }
                }
            }

            if (!expanded) return;
        }

        for (FolderNode sub : node.subdirs.values()) {
            renderFolderNode(dnd, sub, false, indent + (isRoot ? 0 : 1), rowIndex,
                    scrollY, panelX, panelY, panelW, panelH, rowH,
                    mx, my, click, pressed, rightPressed, r, theme, ui, input);
        }

        int fileIndent = (isRoot ? 0 : indent + 1) * 14;
        for (AssetManifestResponse.Entry entry : node.files) {
            renderFileRow(dnd, entry, fileIndent, rowIndex, scrollY, panelX, panelY, panelW, panelH, rowH,
                    mx, my, click, pressed, rightPressed, r, theme, pad, input);
        }
    }

    private void renderFlatFiltered(DragDropManager dnd, FolderNode node, int[] rowIndex,
                                    int scrollY, int panelX, int panelY, int panelW, int panelH,
                                    int rowH, float mx, float my, boolean click, boolean pressed,
                                    boolean rightPressed, UiRenderer r, Theme theme, Ui ui, UiInput input) {
        int pad = theme.design.space_sm;
        for (AssetManifestResponse.Entry entry : node.files) {
            renderFileRow(dnd, entry, 0, rowIndex, scrollY, panelX, panelY, panelW, panelH, rowH,
                    mx, my, click, pressed, rightPressed, r, theme, pad, input);
        }
        for (FolderNode sub : node.subdirs.values()) {
            renderFlatFiltered(dnd, sub, rowIndex, scrollY, panelX, panelY, panelW, panelH, rowH,
                    mx, my, click, pressed, rightPressed, r, theme, ui, input);
        }
    }

    private void renderGridView(DragDropManager dnd, UiInput input, UiRenderer r, Theme theme, Ui ui,
                                int panelX, int panelY, int panelW, int panelH, boolean interactive) {
        r.drawRect(panelX, panelY, panelW, panelH, Theme.toArgb(theme.panelBg));

        int cellSize = 80;
        int cellPad = 6;
        int labelH = 16;
        int totalCell = cellSize + cellPad;
        int cols = Math.max(1, (panelW - cellPad) / totalCell);

        List<AssetManifestResponse.Entry> files = flatVisibleFiles;
        int rows = (files.size() + cols - 1) / cols;
        int contentH = rows * (totalCell + labelH) + cellPad;

        Ui.ScrollArea area = ui.beginScrollArea(r, "assetsFsGridScroll", panelX, panelY, panelW, panelH, contentH);
        int scrollY = (int) area.scrollY();

        float mx = input != null ? input.mousePos().x : -1;
        float my = input != null ? input.mousePos().y : -1;
        boolean click = input != null && interactive && input.mouseReleased();
        boolean pressed = input != null && interactive && input.mousePressed();
        boolean rightPressed = interactive && runtime.rightPressed();

        int startX = panelX + cellPad;

        for (int i = 0; i < files.size(); i++) {
            AssetManifestResponse.Entry entry = files.get(i);
            int col = i % cols;
            int row = i / cols;

            int cx = startX + col * totalCell;
            int cy = panelY + cellPad + row * (totalCell + labelH) - scrollY;

            if (cy + totalCell + labelH < panelY || cy > panelY + panelH) continue;

            boolean selected = selectedEntries.contains(entry);
            boolean hovered = mx >= cx && my >= cy && mx < cx + cellSize && my < cy + cellSize + labelH;

            if (selected) {
                r.drawRoundedRect(cx - 2, cy - 2, cellSize + 4, cellSize + labelH + 4, 4,
                        Theme.mulAlpha(Theme.toArgb(theme.widgetActive), 0.25f));
            } else if (hovered) {
                r.drawRoundedRect(cx - 2, cy - 2, cellSize + 4, cellSize + labelH + 4, 4,
                        Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.35f));
            }

            AssetType assetType = entry.meta() != null ? entry.meta().type() : null;
            Texture thumb = AssetThumbnails.get(entry);
            if (thumb != null) {
                r.drawTexturedRect(thumb, cx, cy, cellSize, cellSize, 0f, 0f, 1f, 1f, 0xFFFFFFFF);
            } else {
                r.drawRoundedRect(cx, cy, cellSize, cellSize, 4, Theme.mulAlpha(Theme.toArgb(theme.widgetBg), 0.40f));
                float iconSize = Math.min(32, cellSize - 16);
                Icon fileIcon = iconFor(assetType);
                int iconCol = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.70f);
                drawIcon(r, theme, assetType != null ? assetType.name().toLowerCase(Locale.ROOT) : "file",
                        fileIcon, cx + (cellSize - iconSize) * 0.5f, cy + (cellSize - iconSize) * 0.5f, iconSize, iconCol);
            }

            String path = entry.path() != null ? entry.path().value() : "";
            String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            String label = ellipsize(r, filename, cellSize);
            float labelW = r.measureText(label);
            r.drawText(label, cx + (cellSize - labelW) * 0.5f, r.baselineForBox(cy + cellSize, labelH),
                    Theme.toArgb(theme.text));

            boolean ctrlDown = input != null && input.ctrlDown();
            boolean shiftDown = input != null && input.shiftDown();

            if (hovered && click) {
                long now = System.currentTimeMillis();
                boolean doubleClick = entry.equals(lastFsClickEntry) && (now - lastFsClickTime) < 300;
                lastFsClickEntry = entry;
                lastFsClickTime = now;

                if (doubleClick && !ctrlDown && !shiftDown) {
                    if (path.endsWith(".moud.scene")) {
                        openSceneFromPath(path);
                    } else if (assetType == AssetType.TEXT) {
                        if (path.startsWith("res://scripts/")
                                && (path.endsWith(".js") || path.endsWith(".mjs") || path.endsWith(".cjs") || path.endsWith(".luau"))) {
                            runtime.openScriptEditor(0L, path);
                        } else {
                            runtime.openTextAssetEditor(path, entry.meta() == null ? null : entry.meta().hash());
                        }
                    }
                } else {
                    handleFileClick(entry, ctrlDown, shiftDown);
                }
            }

            if (hovered && pressed && dnd != null && !dnd.isBusy()) {
                if (path.endsWith(".moud.scene")) {
                    String sid = sceneIdFromPath(path);
                    if (sid != null && !sid.isBlank()) {
                        dnd.armDrag(EditorDnD.sceneId(sid), "Open: " + filename, mx, my);
                    }
                } else if (assetType == AssetType.IMAGE) {
                    dnd.armDrag(EditorDnD.imagePath(path), filename, mx, my);
                } else {
                    dnd.armDrag(EditorDnD.assetPath(path), filename, mx, my);
                }
            }

            if (hovered && rightPressed && !assetContextMenu.isOpen()) {
                if (!selectedEntries.contains(entry)) {
                    selectSingle(entry);
                }
                openAssetContextMenu(entry);
                EditorUiUtil.openMenuClamped(assetContextMenu, runtime, (int) mx, (int) my);
            }
        }

        ui.endScrollArea(area);
        drawScrollbar(r, theme, panelX, panelY, panelW, panelH, contentH, area.scrollY());
        renderAssetContextMenu(r, theme, input, pressed);
    }

    private void renderFileRow(DragDropManager dnd, AssetManifestResponse.Entry entry, int indentPx, int[] rowIndex,
                               int scrollY, int panelX, int panelY, int panelW, int panelH,
                               int rowH, float mx, float my, boolean click, boolean pressed,
                               boolean rightPressed, UiRenderer r, Theme theme, int pad, UiInput input) {
        int rowY = panelY + rowIndex[0] * rowH - scrollY;
        rowIndex[0]++;

        if (rowY + rowH <= panelY || rowY >= panelY + panelH) return;

        String path = entry.path() != null ? entry.path().value() : "";
        boolean selected = selectedEntries.contains(entry);
        boolean hovered = mx >= panelX && my >= rowY && mx < panelX + panelW && my < rowY + rowH;

        if (selected) {
            r.drawRect(panelX, rowY, panelW, rowH, Theme.mulAlpha(Theme.toArgb(theme.widgetActive), 0.22f));
        } else if (hovered) {
            r.drawRect(panelX, rowY, panelW, rowH, Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.45f));
        }

        int textX = panelX + pad + indentPx;
        float iconSize = Math.min(14f, rowH - 4f);
        AssetType assetType = entry.meta() != null ? entry.meta().type() : null;
        String iconKey = assetType != null ? assetType.name().toLowerCase(Locale.ROOT) : "file";
        Icon fileIcon = iconFor(assetType);
        int iconCol = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.85f);
        drawIcon(r, theme, iconKey, fileIcon, textX, rowY + (rowH - iconSize) * 0.5f, iconSize, iconCol);

        int labelX = textX + (int) Math.ceil(iconSize) + 4;
        String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        int metaReserve = (!gridView && entry.meta() != null) ? 100 : 0;
        int maxW = Math.max(0, panelX + panelW - pad - labelX - metaReserve);
        String label = ellipsize(r, filename, maxW);
        r.drawText(label, labelX, r.baselineForBox(rowY, rowH), Theme.toArgb(theme.text));

        if (!gridView && entry.meta() != null) {
            String sizeText = formatSize(entry.meta().sizeBytes());
            float sizeW = r.measureText(sizeText);
            int metaColor = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.60f);
            float metaX = panelX + panelW - pad - sizeW;
            r.drawText(sizeText, metaX, r.baselineForBox(rowY, rowH), metaColor);

            String typeTag = entry.meta().type().name().substring(0, Math.min(3, entry.meta().type().name().length()));
            float tagW = r.measureText(typeTag);
            float tagX = metaX - tagW - pad;
            r.drawText(typeTag, tagX, r.baselineForBox(rowY, rowH), metaColor);
        }

        boolean ctrlDown = input != null && input.ctrlDown();
        boolean shiftDown = input != null && input.shiftDown();

        if (hovered && click) {
            long now = System.currentTimeMillis();
            boolean doubleClick = entry.equals(lastFsClickEntry) && (now - lastFsClickTime) < 300;
            lastFsClickEntry = entry;
            lastFsClickTime = now;

            if (doubleClick && !ctrlDown && !shiftDown) {
                if (path.endsWith(".moud.scene")) {
                    openSceneFromPath(path);
                } else if (assetType == AssetType.TEXT) {
                    if (path.startsWith("res://scripts/")
                            && (path.endsWith(".js") || path.endsWith(".mjs") || path.endsWith(".cjs") || path.endsWith(".luau"))) {
                        runtime.openScriptEditor(0L, path);
                    } else {
                        runtime.openTextAssetEditor(path, entry.meta() == null ? null : entry.meta().hash());
                    }
                }
            } else {
                handleFileClick(entry, ctrlDown, shiftDown);
            }
        }

        // Universal drag-and-drop
        if (hovered && pressed && dnd != null && !dnd.isBusy()) {
            if (path.endsWith(".moud.scene")) {
                String sid = sceneIdFromPath(path);
                if (sid != null && !sid.isBlank()) {
                    dnd.armDrag(EditorDnD.sceneId(sid), "Open: " + filename, mx, my);
                }
            } else if (assetType == AssetType.IMAGE) {
                dnd.armDrag(EditorDnD.imagePath(path), filename, mx, my);
            } else {
                dnd.armDrag(EditorDnD.assetPath(path), filename, mx, my);
            }
        }

        if (hovered && rightPressed && !assetContextMenu.isOpen()) {
            if (!selectedEntries.contains(entry)) {
                selectSingle(entry);
            }
            openAssetContextMenu(entry);
            EditorUiUtil.openMenuClamped(assetContextMenu, runtime, (int) mx, (int) my);
        }
    }

    private int countVisibleRows(FolderNode node, boolean isRoot) {
        int count = isRoot ? 0 : 1;
        if (isRoot || expandedFolders.contains(node.fullPath)) {
            for (FolderNode sub : node.subdirs.values()) count += countVisibleRows(sub, false);
            count += node.files.size();
        }
        return count;
    }

    private int countFlatFiles(FolderNode node) {
        int count = node.files.size();
        for (FolderNode sub : node.subdirs.values()) count += countFlatFiles(sub);
        return count;
    }

    private void rebuildFolderTree(String filter) {
        FolderNode root = new FolderNode("res://", "res://");
        String f = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        for (AssetManifestResponse.Entry entry : entries) {
            if (entry == null || entry.path() == null || entry.meta() == null) continue;
            String path = entry.path().value();
            if (path == null) continue;
            String afterSchemeCheck = path.startsWith("res://") ? path.substring(6) : path;
            if (afterSchemeCheck.startsWith("blobs/") || afterSchemeCheck.equals("blobs")) continue;
            if (!f.isEmpty() && !path.toLowerCase(Locale.ROOT).contains(f)) continue;
            String remaining = path.startsWith("res://") ? path.substring(6) : path;
            insertEntry(root, entry, remaining);
        }
        fsRoot = root;
        autoExpandSingleChildFolders(root);
        sortFolderNode(root);
        flatVisibleFiles.clear();
        collectFlatFiles(root, flatVisibleFiles);
        flatVisibleFiles.sort(currentSortComparator());
    }

    private static void insertEntry(FolderNode node, AssetManifestResponse.Entry entry, String remaining) {
        int slash = remaining.indexOf('/');
        if (slash < 0) {
            node.files.add(entry);
            return;
        }
        String segment = remaining.substring(0, slash);
        String rest = remaining.substring(slash + 1);
        if (segment.isEmpty()) {
            insertEntry(node, entry, rest);
            return;
        }
        String subPath = node.fullPath.equals("res://") ? "res://" + segment : node.fullPath + "/" + segment;
        FolderNode sub = node.subdirs.computeIfAbsent(segment, k -> new FolderNode(k, subPath));
        insertEntry(sub, entry, rest);
    }

    private static final class FolderNode {
        final String name;
        final String fullPath;
        final Map<String, FolderNode> subdirs = new LinkedHashMap<>();
        final List<AssetManifestResponse.Entry> files = new ArrayList<>();

        FolderNode(String name, String fullPath) {
            this.name = name;
            this.fullPath = fullPath;
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
        int btnSize = theme.design.widget_height_md;
        int rightW = activeDockTab == 0 ? (btnSize * 3 + pad * 2) : (52 * 3 + pad * 2);
        int searchW = Math.max(120, w - pad * 3 - rightW);
        int searchX = x + pad;
        int searchY = y + (h - searchH) / 2;

        filterField.render(r, uiContext, input, theme, searchX, searchY, searchW, searchH, true);
        if ((filterField.text() == null || filterField.text().isEmpty()) && (uiContext == null || !filterField.isFocused(uiContext))) {
            int hint = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.70f);
            int sp = theme.design.space_sm;
            float iconSize = Math.min(theme.design.icon_sm, searchH - sp * 2);
            MoudIcons.drawOrFallback(r, theme, Icon.SEARCH, searchX + sp, searchY + (searchH - iconSize) * 0.5f, iconSize, hint);
            r.drawText(activeDockTab == 0 ? "Filter Files" : "Filter Scenes",
                    searchX + sp + iconSize + sp, r.baselineForBox(searchY, searchH), hint);
        }

        int btnY = y + (h - btnSize) / 2;
        if (activeDockTab == 0) {
            int iconBtnW = btnSize;
            int gap = pad;

            int refreshX = x + w - gap - iconBtnW;
            int uploadX = refreshX - gap - iconBtnW;
            int newX = uploadX - gap - iconBtnW;

            EditorUiUtil.iconButton(ui, r, theme, newX, btnY, iconBtnW, btnSize, Icon.FILE, interactive, () -> runtime.openCreateAsset());
            EditorUiUtil.iconButton(ui, r, theme, uploadX, btnY, iconBtnW, btnSize, Icon.ADD, interactive, () -> AssetImportUtil.importAssetFile(runtime));
            EditorUiUtil.iconButton(ui, r, theme, refreshX, btnY, iconBtnW, btnSize, Icon.SNAP, interactive, () -> {
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

        EditorUiUtil.textButton(ui, r, theme, newX, btnY, newW, btnSize, "New", interactive, () -> {
            createSceneOpen = !createSceneOpen;
            createSceneError = null;
            if (createSceneOpen) {
                createSceneFocusRequested = true;
            }
        });

        EditorUiUtil.textButton(ui, r, theme, impX, btnY, impW, btnSize, "Import", interactive, () -> {
            AssetImportUtil.importSceneFile(runtime);
        });

        EditorUiUtil.textButton(ui, r, theme, delX, btnY, delW, btnSize, delLabel, interactive && canDelete, () -> {
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

    private void renderBreadcrumbs(Ui ui, UiRenderer r, Theme theme, UiInput input,
                                   int x, int y, int w, int h, boolean interactive) {
        int bg = Theme.toArgb(theme.headerBg);
        r.drawRect(x, y, w, h, bg);
        r.drawRect(x, y + h - 1, w, 1, Theme.toArgb(theme.headerLine));

        int pad = theme.design.space_sm;
        int textColor = Theme.toArgb(theme.textMuted);
        int hoverColor = Theme.toArgb(theme.text);
        int sepColor = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.50f);

        int btnH = Math.min(h - 4, theme.design.widget_height_md);
        int btnY = y + (h - btnH) / 2;
        int btnW = btnH;
        int sortW = 36;
        int rightX = x + w - pad;

        rightX -= btnW;
        EditorUiUtil.iconButton(ui, r, theme, rightX, btnY, btnW, btnH,
                Icon.CHEVRON_DOWN, interactive, () -> {
                    if (fsRoot != null) expandAllFolders(fsRoot);
                });
        rightX -= pad;

        rightX -= btnW;
        EditorUiUtil.iconButton(ui, r, theme, rightX, btnY, btnW, btnH,
                Icon.CHEVRON_RIGHT, interactive, () -> {
                    expandedFolders.clear();
                    if (fsRoot != null) autoExpandSingleChildFolders(fsRoot);
                });
        rightX -= pad;

        rightX -= sortW;
        String sortLabel = sortMode == 0 ? "A-Z" : (sortMode == 1 ? "Typ" : "Sz");
        EditorUiUtil.stepButton(ui, r, theme, rightX, btnY, sortW, btnH, sortLabel, interactive, () -> {
            sortMode = (sortMode + 1) % 3;
            rebuildFolderTree(filterField.text());
        });
        rightX -= pad;

        rightX -= btnW;
        EditorUiUtil.toggleButton(ui, r, theme, rightX, btnY, btnW, btnH,
                Icon.GRID, gridView, interactive, () -> { gridView = !gridView; });
        rightX -= pad;

        int breadcrumbMaxX = rightX;

        String path = currentBrowsePath;
        if (path == null || path.isBlank()) path = "res://";

        String afterScheme = path.startsWith("res://") ? path.substring(6) : path;
        String[] parts = afterScheme.isEmpty() ? new String[0] : afterScheme.split("/");

        float cx = x + pad;
        float bmy = input != null ? input.mousePos().y : -1;
        float bmx = input != null ? input.mousePos().x : -1;
        boolean click = input != null && interactive && input.mouseReleased();
        float baseline = r.baselineForBox(y, h);

        String rootLabel = "res://";
        float rootW = r.measureText(rootLabel);
        boolean rootHovered = bmx >= cx && bmx < cx + rootW && bmy >= y && bmy < y + h;
        r.drawText(rootLabel, cx, baseline, rootHovered ? hoverColor : textColor);
        if (rootHovered && click) {
            currentBrowsePath = "res://";
            expandedFolders.clear();
            if (fsRoot != null) autoExpandSingleChildFolders(fsRoot);
        }
        cx += rootW;

        StringBuilder accumulated = new StringBuilder("res://");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            accumulated.append(part).append("/");

            r.drawText("/", cx, baseline, sepColor);
            cx += r.measureText("/");

            float partW = r.measureText(part);
            if (cx + partW > breadcrumbMaxX) break;

            boolean partHovered = bmx >= cx && bmx < cx + partW && bmy >= y && bmy < y + h;
            r.drawText(part, cx, baseline, partHovered ? hoverColor : textColor);

            if (partHovered && click) {
                String target = accumulated.toString();
                currentBrowsePath = target;
                StringBuilder expanding = new StringBuilder("res://");
                for (String seg : target.substring(6).split("/")) {
                    if (seg.isEmpty()) continue;
                    expanding.append(seg);
                    expandedFolders.add(expanding.toString());
                    expanding.append("/");
                }
            }
            cx += partW;
        }
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
        DragDropManager dnd = uiContext != null ? uiContext.dragDrop() : null;

        String filter = filterField.text() == null ? "" : filterField.text().trim().toLowerCase(Locale.ROOT);

        if (createSceneOpen) {
            int rowH  = 28;
            int fieldH = 22;
            int btnW   = 70;
            int cancelW = 70;
            int modeW  = 44;

            int row1Y   = cursorY + (rowH - fieldH) / 2;
            int modeX   = x + w - pad - modeW;
            int idX     = x + pad;
            int idW     = modeX - pad - idX;

            createSceneIdField.render(r, uiContext, input, theme, idX, row1Y, idW, fieldH, true);
            if ((createSceneIdField.text() == null || createSceneIdField.text().isEmpty())
                    && (uiContext == null || !createSceneIdField.isFocused(uiContext))) {
                r.drawText("scene_id", idX + 6, r.baselineForBox(row1Y, fieldH),
                        Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.65f));
            }

            String modeLabel = createScene2D ? "2D" : "3D";
            EditorUiUtil.stepButton(ui, r, theme, modeX, row1Y, modeW, fieldH, modeLabel,
                    interactive, () -> createScene2D = !createScene2D);

            cursorY += rowH + pad;

            int row2Y   = cursorY + (rowH - fieldH) / 2;
            int cancelX = x + w - pad - cancelW;
            int createX = cancelX - pad - btnW;
            int nameX   = x + pad;
            int nameW   = createX - pad - nameX;

            createSceneNameField.render(r, uiContext, input, theme, nameX, row2Y, nameW, fieldH, true);
            if ((createSceneNameField.text() == null || createSceneNameField.text().isEmpty())
                    && (uiContext == null || !createSceneNameField.isFocused(uiContext))) {
                r.drawText("Display name (optional)", nameX + 6, r.baselineForBox(row2Y, fieldH),
                        Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.65f));
            }

            EditorUiUtil.textButton(ui, r, theme, createX, row2Y, btnW, fieldH, "Create", interactive, () -> {
                String sid = normalizeSceneId(createSceneIdField.text());
                if (!isValidSceneId(sid)) {
                    createSceneError = "Invalid id (use [a-z0-9_-], max 64 chars)";
                    return;
                }
                String dn = createSceneNameField.text();
                if (createScene2D) {
                    runtime.markSceneMode(sid, EditorRuntime.ViewportMode.TWO_D);
                }
                runtime.net().createScene(runtime.session(), sid, dn);
                if (state != null) {
                    state.pendingSnapshot = true;
                }
                createSceneOpen = false;
                createSceneError = null;
                createScene2D = false;
                createSceneIdField.setText("");
                createSceneNameField.setText("");
            });

            EditorUiUtil.textButton(ui, r, theme, cancelX, row2Y, cancelW, fieldH, "Cancel", interactive, () -> {
                createSceneOpen = false;
                createSceneError = null;
            });

            if (interactive && createSceneFocusRequested && uiContext != null) {
                createSceneFocusRequested = false;
                createSceneIdField.focus(uiContext);
            }

            cursorY += rowH + pad;
            if (createSceneError != null && !createSceneError.isBlank()) {
                r.drawText(createSceneError, x + pad, r.baselineForBox(cursorY, 18),
                        Theme.toArgb(theme.danger));
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
            drawIcon(r, theme, "scene", Icon.FILE, textX, rowY + (rowH - iconSize) * 0.5f, iconSize, Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.85f));
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
                if (dnd != null && !dnd.isBusy()) {
                    dnd.armDrag(EditorDnD.sceneId(sceneId), "Open: " + filename, mx, my);
                }
            }

            if (hovered && click) {
                boolean draggingScene = dnd != null
                        && dnd.isDragging()
                        && dnd.dragPayload() != null
                        && EditorDnD.TYPE_SCENE_ID.equals(dnd.dragPayload().type());
                if (!draggingScene) {
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
        drawScrollbar(r, theme, x, listY, w, listH, contentHeight, area.scrollY());

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

    private void openSceneFromPath(String path) {
        String filename = path.substring(path.lastIndexOf('/') + 1);
        if (!filename.endsWith(".moud.scene")) {
            return;
        }
        String sceneId = sceneIdFromPath(path);
        if (sceneId == null || sceneId.isBlank()) return;
        EditorState state = runtime.state();
        if (state != null) {
            state.ensureSceneOpen(sceneId);
            runtime.net().selectScene(runtime.session(), state, sceneId);
        }
    }

    private static String sceneIdFromPath(String path) {
        if (path == null) return null;
        String filename = path.substring(path.lastIndexOf('/') + 1);
        if (!filename.endsWith(".moud.scene")) return null;
        return filename.substring(0, filename.length() - ".moud.scene".length());
    }

    private void openAssetContextMenu(AssetManifestResponse.Entry entry) {
        assetContextMenu.clear();
        if (entry == null || entry.path() == null) return;

        String path = entry.path().value();
        if (path == null || path.isBlank()) return;
        AssetType type = entry.meta() != null ? entry.meta().type() : null;
        String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;

        if (path.endsWith(".moud.scene")) {
            assetContextMenu.addItem("Open Scene", Icon.PLAY, () -> openSceneFromPath(path));
        } else if (type == AssetType.TEXT) {
            if (path.startsWith("res://scripts/")
                    && (path.endsWith(".js") || path.endsWith(".mjs") || path.endsWith(".cjs") || path.endsWith(".luau"))) {
                assetContextMenu.addItem("Edit Script", Icon.CODE, () -> runtime.openScriptEditor(0L, path));
            } else {
                assetContextMenu.addItem("Edit", Icon.TEXT, () -> runtime.openTextAssetEditor(path, entry.meta() == null ? null : entry.meta().hash()));
            }
        }

        assetContextMenu.addSeparator();

        assetContextMenu.addItem("Copy Path", "Ctrl+C", () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.keyboard != null) {
                mc.keyboard.setClipboard(path);
            }
        });

        assetContextMenu.addItem("Copy Filename", () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.keyboard != null) {
                mc.keyboard.setClipboard(filename);
            }
        });

        assetContextMenu.addSeparator();

        if (entry.meta() != null) {
            String sizeStr = formatSize(entry.meta().sizeBytes());
            String typeStr = entry.meta().type().name().toLowerCase(Locale.ROOT);
            assetContextMenu.addInfo(typeStr + " \u2014 " + sizeStr);
        }
    }

    private static void drawIcon(UiRenderer r, Theme theme, String moudKey, Icon fallback,
                                 float x, float y, float size, int tintArgb) {
        if (MoudIcons.has(moudKey)) {
            MoudIcons.draw(r, moudKey, x, y, size, tintArgb);
        } else {
            MoudIcons.drawOrFallback(r, theme, fallback, x, y, size, tintArgb);
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

    private Comparator<AssetManifestResponse.Entry> currentSortComparator() {
        return switch (sortMode) {
            case 1 -> Comparator.comparing(
                    (AssetManifestResponse.Entry e) -> e.meta() != null ? e.meta().type().name() : "",
                    String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(e -> e.path() != null ? e.path().value() : "", String.CASE_INSENSITIVE_ORDER);
            case 2 -> Comparator.comparingLong(
                    (AssetManifestResponse.Entry e) -> e.meta() != null ? e.meta().sizeBytes() : 0L)
                    .reversed()
                    .thenComparing(e -> e.path() != null ? e.path().value() : "", String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(
                    (AssetManifestResponse.Entry e) -> e.path() != null ? e.path().value() : "",
                    String.CASE_INSENSITIVE_ORDER);
        };
    }

    private static void collectFlatFiles(FolderNode node, List<AssetManifestResponse.Entry> out) {
        out.addAll(node.files);
        for (FolderNode sub : node.subdirs.values()) {
            collectFlatFiles(sub, out);
        }
    }

    private void sortFolderNode(FolderNode node) {
        node.files.sort(currentSortComparator());
        for (FolderNode sub : node.subdirs.values()) {
            sortFolderNode(sub);
        }
    }

    private void autoExpandSingleChildFolders(FolderNode node) {
        if (node == null) return;
        for (FolderNode sub : node.subdirs.values()) {
            if (sub.subdirs.size() == 1 && sub.files.isEmpty()) {
                expandedFolders.add(sub.fullPath);
            }
            autoExpandSingleChildFolders(sub);
        }
    }

    private void expandAllFolders(FolderNode node) {
        if (node == null) return;
        for (FolderNode sub : node.subdirs.values()) {
            expandedFolders.add(sub.fullPath);
            expandAllFolders(sub);
        }
    }

    private void selectSingle(AssetManifestResponse.Entry entry) {
        selectedEntries.clear();
        selectedEntries.add(entry);
        selectedFsEntry = entry;
        lastShiftAnchor = entry;
    }

    private void selectToggle(AssetManifestResponse.Entry entry) {
        if (selectedEntries.contains(entry)) {
            selectedEntries.remove(entry);
            selectedFsEntry = selectedEntries.isEmpty() ? null : selectedEntries.iterator().next();
        } else {
            selectedEntries.add(entry);
            selectedFsEntry = entry;
        }
        lastShiftAnchor = entry;
    }

    private void selectRange(AssetManifestResponse.Entry entry) {
        if (lastShiftAnchor == null) {
            selectSingle(entry);
            return;
        }
        int anchorIdx = flatVisibleFiles.indexOf(lastShiftAnchor);
        int targetIdx = flatVisibleFiles.indexOf(entry);
        if (anchorIdx < 0 || targetIdx < 0) {
            selectSingle(entry);
            return;
        }
        int lo = Math.min(anchorIdx, targetIdx);
        int hi = Math.max(anchorIdx, targetIdx);
        selectedEntries.clear();
        for (int i = lo; i <= hi; i++) {
            selectedEntries.add(flatVisibleFiles.get(i));
        }
        selectedFsEntry = entry;
    }

    private void handleFileClick(AssetManifestResponse.Entry entry, boolean ctrlDown, boolean shiftDown) {
        if (shiftDown) {
            selectRange(entry);
        } else if (ctrlDown) {
            selectToggle(entry);
        } else {
            selectSingle(entry);
        }
    }

    private void renderAssetContextMenu(UiRenderer r, Theme theme, UiInput input, boolean pressed) {
        if (!assetContextMenu.isOpen()) return;
        int itemH = Math.max(18, theme.tokens.itemHeight);
        if (input != null) {
            assetContextMenu.updateFromInput(input, theme, itemH);
            EditorUiUtil.clampOpenMenuToScreen(assetContextMenu, runtime);
        }
        assetContextMenu.render(r, theme, itemH,
                Theme.toArgb(theme.panelBg),
                Theme.toArgb(theme.widgetHover),
                Theme.toArgb(theme.text),
                assetContextMenu.hoverIndex());
        if (input != null && pressed) {
            float mx = input.mousePos().x;
            float my = input.mousePos().y;
            assetContextMenu.handleClick((int) mx, (int) my, itemH);
        }
    }

    private static void drawScrollbar(UiRenderer r, Theme theme, int x, int y, int w, int h,
                                      int contentH, float scrollY) {
        if (contentH <= h || h <= 0) return;

        int barW = 4;
        int barX = x + w - barW - 1;
        float ratio = (float) h / contentH;
        int thumbH = Math.max(16, Math.round(h * ratio));
        float maxScroll = contentH - h;
        float scrollFrac = maxScroll > 0 ? scrollY / maxScroll : 0f;
        int thumbY = y + Math.round((h - thumbH) * scrollFrac);

        r.drawRoundedRect(barX, y, barW, h, 2, Theme.mulAlpha(Theme.toArgb(theme.widgetBg), 0.15f));
        r.drawRoundedRect(barX, thumbY, barW, thumbH, 2, Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.40f));
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
