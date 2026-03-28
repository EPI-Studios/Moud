package com.moud.client.fabric.editor.panels;

import com.miry.platform.InputConstants;
import com.miry.ui.UiContext;
import com.miry.ui.PanelContext;
import com.miry.ui.Ui;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.event.TextInputEvent;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.theme.Icon;
import com.miry.ui.input.UiInput;
import com.miry.ui.widgets.ContextMenu;
import com.miry.ui.widgets.StripTabs;
import com.miry.ui.widgets.TextField;
import com.miry.ui.widgets.TreeNode;
import com.miry.ui.widgets.TreeView;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.theme.EditorTheme;
import com.moud.client.fabric.util.ParseUtils;
import com.moud.client.fabric.editor.util.EditorUiUtil;
import com.moud.client.fabric.render.MoudIcons;
import com.moud.client.fabric.assets.AssetsClient;
import com.moud.core.NodeTypeDef;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.core.scene.Node;
import com.moud.core.scene.SceneFile;
import com.moud.net.protocol.AssetTransferStatus;
import com.moud.net.protocol.AssetUploadAck;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.session.Session;
import com.moud.client.fabric.editor.state.EditorHistory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.HashMap;
import java.util.Locale;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import com.miry.ui.clipboard.Clipboard;
import com.moud.client.fabric.editor.net.EditorNet;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class ScenePanel extends Panel {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PROP_VISIBLE = "visible";
    private static final String PROP_LOCKED = "@locked";
    private static final String PROP_EDITOR_LOCKED = "editor_locked";

    private final EditorRuntime runtime;

    private final StripTabs dockTabs = new StripTabs();
    private final StripTabs.Style dockTabStyle = new StripTabs.Style();

    private final TextField filterField = new TextField();
    private final ContextMenu nodeMenu = new ContextMenu();
    private final ContextMenu addChildMenu = new ContextMenu();
    private final ArrayList<ContextMenu> addChildCategoryMenus = new ArrayList<>();

    private TreeView<SceneSnapshot.NodeSnapshot> treeView;
    private TreeNode<SceneSnapshot.NodeSnapshot> rootNode;
    private final TreeView.Style treeStyle = new TreeView.Style();

    private long renamingNodeId = -1;
    private final TextField renameTreeField = new TextField();
    private int renameFieldX;
    private int renameFieldY;
    private int renameFieldW;
    private int renameFieldH;
    private String lastFilter = "";

    private boolean saveBranchOpen;
    private boolean saveBranchFocusRequested;
    private long saveBranchRootId;
    private String saveBranchError;
    private final TextField saveBranchSceneIdField = new TextField();
    private final TextField saveBranchDisplayNameField = new TextField();
    private long lastRev = Long.MIN_VALUE;
    private String lastSceneId = "";

    private record ClipboardEntry(String name, String type, List<SceneSnapshot.Property> properties) {}
    private final List<ClipboardEntry> clipboard = new ArrayList<>();
    private long cutNodeId = -1;

    private boolean suppressHistory;

    private long filterChangedAtMs;
    private boolean renameJustCancelled;
    private final Set<Long> expandedNodeIds = new HashSet<>();

    private UiContext lastUiContext;

    public ScenePanel(EditorRuntime runtime) {
        super("");
        this.runtime = runtime;
        rebuildTree(runtime.state(), "");
    }

    public void handleKey(UiContext ctx, KeyEvent e) {
        if (ctx == null || e == null) {
            return;
        }
        lastUiContext = ctx;
        if (e.isPressOrRepeat() && (e.hasCtrl() || e.hasSuper()) && e.key() == InputConstants.KEY_F) {
            filterField.focus(ctx);
            filterChangedAtMs = System.currentTimeMillis();
            return;
        }
        if (renamingNodeId >= 0) {
            renameTreeField.handleKey(e, ctx.clipboard());
            if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ENTER) {
                commitInlineRename();
            } else if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ESCAPE) {
                renamingNodeId = -1;
            }
            return;
        }
        if (saveBranchOpen) {
            if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ESCAPE) {
                saveBranchOpen = false;
                saveBranchError = null;
                return;
            }
            if (saveBranchSceneIdField.isFocused(ctx)) {
                saveBranchSceneIdField.handleKey(e, ctx.clipboard());
                if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ENTER) {
                    commitSaveBranch();
                }
                return;
            }
            if (saveBranchDisplayNameField.isFocused(ctx)) {
                saveBranchDisplayNameField.handleKey(e, ctx.clipboard());
                if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ENTER) {
                    commitSaveBranch();
                }
                return;
            }
        }
        if (filterField.isFocused(ctx)) {
            if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ENTER) {
                String filterText = filterField.text();
                if (filterText != null && !filterText.isBlank()) {
                    jumpToFirstFilterMatch(filterText.trim().toLowerCase(java.util.Locale.ROOT));
                }
                return;
            }
            filterField.handleKey(e, ctx.clipboard());
            filterChangedAtMs = System.currentTimeMillis();
            return;
        }
        if (treeView != null && treeView.isFocused(ctx) && e.isPressOrRepeat()) {
            if (e.key() == InputConstants.KEY_ESCAPE) {
                EditorState state = runtime.state();
                if (state != null) state.selectedId = 0L;
                clearTreeSelection();
                return;
            }
            if (e.key() == InputConstants.KEY_UP || e.key() == InputConstants.KEY_DOWN) {
                navigateTree(e.key() == InputConstants.KEY_DOWN);
                return;
            }
            if (e.key() == InputConstants.KEY_LEFT) {
                navigateTreeLeft();
                return;
            }
            if (e.key() == InputConstants.KEY_RIGHT) {
                navigateTreeRight();
                return;
            }
            if (e.key() == InputConstants.KEY_F2) {
                EditorState state = runtime.state();
                SceneSnapshot.NodeSnapshot selected = state != null ? state.scene.getNode(state.selectedId) : null;
                if (selected != null) {
                    beginInlineRename(selected);
                }
                return;
            }
            if (e.key() == InputConstants.KEY_DELETE) {
                EditorState state = runtime.state();
                SceneSnapshot.NodeSnapshot selected = state != null ? state.scene.getNode(state.selectedId) : null;
                if (selected != null && selected.parentId() != 0L) {
                    queueFree(selected.nodeId());
                }
                return;
            }
            boolean ctrl = e.hasCtrl() || e.hasSuper();
            if (ctrl && e.key() == InputConstants.KEY_Z) {
                performUndo();
                return;
            }
            if (ctrl && (e.key() == InputConstants.KEY_Y || (e.hasShift() && e.key() == InputConstants.KEY_Z))) {
                performRedo();
                return;
            }
            if (ctrl && e.key() == InputConstants.KEY_C) {
                copySelectedNode();
                return;
            }
            if (ctrl && e.key() == InputConstants.KEY_X) {
                cutSelectedNode();
                return;
            }
            if (ctrl && e.key() == InputConstants.KEY_V) {
                if (e.hasShift()) {
                    pasteAsSibling();
                } else {
                    pasteNodes();
                }
                return;
            }
            if (ctrl && e.hasShift() && e.key() == InputConstants.KEY_D) {
                EditorState state = runtime.state();
                SceneSnapshot.NodeSnapshot selected = state != null ? state.scene.getNode(state.selectedId) : null;
                if (selected != null) {
                    duplicateNodeWithOffset(selected);
                }
                return;
            }
            if (ctrl && !e.hasShift() && e.key() == InputConstants.KEY_D) {
                EditorState state = runtime.state();
                SceneSnapshot.NodeSnapshot selected = state != null ? state.scene.getNode(state.selectedId) : null;
                if (selected != null) {
                    duplicateNode(selected);
                }
                return;
            }
            if (ctrl && e.key() == InputConstants.KEY_A) {
                selectAll();
                return;
            }
            if (ctrl && e.key() == InputConstants.KEY_UP) {
                EditorState s = runtime.state();
                SceneSnapshot.NodeSnapshot sel = s != null ? s.scene.getNode(s.selectedId) : null;
                if (sel != null && sel.parentId() != 0L) moveNode(sel, -1);
                return;
            }
            if (ctrl && e.key() == InputConstants.KEY_DOWN) {
                EditorState s = runtime.state();
                SceneSnapshot.NodeSnapshot sel = s != null ? s.scene.getNode(s.selectedId) : null;
                if (sel != null && sel.parentId() != 0L) moveNode(sel, 1);
                return;
            }
        }
        if (treeView != null) {
            treeView.handleKey(ctx, e);
        }
    }

    public void handleTextInput(UiContext ctx, TextInputEvent e) {
        if (ctx == null || e == null) {
            return;
        }
        if (saveBranchOpen) {
            if (saveBranchSceneIdField.isFocused(ctx)) {
                saveBranchSceneIdField.handleTextInput(e);
                return;
            }
            if (saveBranchDisplayNameField.isFocused(ctx)) {
                saveBranchDisplayNameField.handleTextInput(e);
                return;
            }
        }
        if (filterField.isFocused(ctx)) {
            filterField.handleTextInput(e);
            filterChangedAtMs = System.currentTimeMillis();
            return;
        }
        if (renamingNodeId >= 0) {
            renameTreeField.handleTextInput(e);
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
        int pad = theme.design.space_sm;

        int cursorY = y;
        renderDockTabs(ui, r, uiContext, theme, x, cursorY, w, tabH, interactive);
        cursorY += tabH;

        renderToolbar(ui, r, uiContext, theme, x, cursorY, w, toolbarH, interactive);
        cursorY += toolbarH;

        if (saveBranchOpen) {
            cursorY = renderSaveBranchRow(ui, r, uiContext, theme, x, cursorY, w, interactive);
        }

        int treeX = x;
        int treeY = cursorY;
        int treeW = w;
        int treeH = Math.max(0, y + h - treeY);

        EditorState state = runtime.state();
        if (state == null || state.scene == null) {
            r.drawText("(no scene)", x + pad, r.baselineForBox(treeY + pad, 18), Theme.toArgb(theme.textMuted));
            ui.endPanel();
            return;
        }
        if (state.scene.revision() < 0) {
            r.drawText("(loading...)", x + pad, r.baselineForBox(treeY + pad, 18), Theme.toArgb(theme.textMuted));
            ui.endPanel();
            return;
        }

        String filter = filterField.text() == null ? "" : filterField.text().trim();
        String currentSceneId = state.activeSceneId == null ? "" : state.activeSceneId;
        boolean filterChanged = !filter.equals(lastFilter);
        boolean filterReady = !filterChanged || System.currentTimeMillis() - filterChangedAtMs > 150;
        boolean needsRebuild = treeView == null || rootNode == null
                || (filterChanged && filterReady)
                || state.scene.revision() != lastRev
                || !Objects.equals(lastSceneId, currentSceneId);
        if (needsRebuild) {
            nodeMenu.close();
            addChildMenu.close();
            closeAddChildCategoryMenus();
            rebuildTree(state, filter);
            lastFilter = filter;
            lastRev = state.scene.revision();
            lastSceneId = currentSceneId;
        }

        // Player start warning banner
        if (state.scene.revision() >= 0 && !sceneHasPlayerStart(state)) {
            int warnH = 22;
            int warnPad = theme.design.space_sm;
            r.drawRect(treeX, treeY, treeW, warnH, EditorTheme.WARNING_BG);
            r.drawText("No PlayerStart in scene", treeX + warnPad, r.baselineForBox(treeY, warnH), EditorTheme.WARNING_TEXT);
            treeY += warnH;
            treeH = Math.max(0, treeH - warnH);
        }

        if (treeView != null && treeH > 0) {
            int itemH = Math.max(18, theme.tokens.itemHeight);
            int contentHeight = treeView.computeContentHeight();
            Ui.ScrollArea area = ui.beginScrollArea(r, "sceneTreeScroll", treeX, treeY, treeW, treeH, contentHeight);
            int scrollOffset = (int) area.scrollY();

            updateTreeStyle(theme);
            treeView.render(r, uiContext, input, theme, treeX, treeY, treeW, treeH, scrollOffset, true);
            updateSelectionFromTree(state);

            if (renamingNodeId >= 0) {
                renderInlineRename(r, uiContext, theme, input, treeX, treeY, treeW, itemH, scrollOffset);
            }

            float mx = ui.mouse().x;
            float my = ui.mouse().y;

            if (renamingNodeId >= 0 && input != null && input.mousePressed()) {
                boolean hit = mx >= renameFieldX && my >= renameFieldY && mx < renameFieldX + renameFieldW && my < renameFieldY + renameFieldH;
                if (!hit) {
                    renamingNodeId = -1;
                    renameJustCancelled = true;
                }
            }

            if (renamingNodeId < 0 && input != null && input.mousePressed()
                    && mx >= treeX && mx < treeX + treeW && my >= treeY && my < treeY + treeH) {
                int clickRow = (int) ((my - treeY + scrollOffset) / itemH);
                List<TreeView.VisibleNode<SceneSnapshot.NodeSnapshot>> vis = treeView.getVisibleNodes();
                if (clickRow < 0 || clickRow >= vis.size()) {
                    state.selectedId = 0L;
                    clearTreeSelection();
                }
            }

            boolean skipClick = renameJustCancelled;
            renameJustCancelled = false;

            boolean rightPressed = interactive && runtime.rightPressed();
            if (!skipClick && rightPressed && !nodeMenu.isOpen()) {
                if (mx >= treeX && mx < treeX + treeW && my >= treeY && my < treeY + treeH) {
                    treeView.handleClick(input, (int) mx, (int) my, treeX, treeY, treeW, treeH, scrollOffset);
                    updateSelectionFromTree(state);
                    SceneSnapshot.NodeSnapshot selected = state.scene.getNode(state.selectedId);
                    if (selected != null) {
                        openNodeMenu(selected);
                        EditorUiUtil.openMenuClamped(nodeMenu, runtime, (int) mx, (int) my);
                    }
                }
            }

            if (runtime.assetDragActive() && input != null && input.mouseReleased()
                    && mx >= treeX && mx < treeX + treeW && my >= treeY && my < treeY + treeH) {
                String dragPath = runtime.assetDragPath();
                if (dragPath != null && dragPath.endsWith(".js")) {
                    int dropRow = (int) ((my - treeY + scrollOffset) / itemH);
                    List<TreeView.VisibleNode<SceneSnapshot.NodeSnapshot>> vis = treeView.getVisibleNodes();
                    if (dropRow >= 0 && dropRow < vis.size()) {
                        SceneSnapshot.NodeSnapshot dropTarget = vis.get(dropRow).node().data();
                        if (dropTarget != null) {
                            sendOpsRecorded(List.of(new SceneOp.SetProperty(dropTarget.nodeId(), "script", dragPath)));
                            runtime.requestToast("Attached: " + dragPath, false, 1500);
                        }
                    }
                }
                runtime.clearAssetDrag();
            }

            ui.endScrollArea(area);

            if (nodeMenu.isOpen()) {
                if (input != null) {
                    nodeMenu.updateFromInput(input, theme, itemH);
                    EditorUiUtil.clampOpenMenuToScreen(nodeMenu, runtime);
                }
                if (interactive) {
                    syncSubmenus(ui, theme, itemH);
                }
                if (input != null && input.mousePressed()) {
                    int cmx = (int) ui.mouse().x;
                    int cmy = (int) ui.mouse().y;
                    boolean insideAny = inside(nodeMenu, cmx, cmy, itemH)
                            || (addChildMenu.isOpen() && inside(addChildMenu, cmx, cmy, itemH));
                    if (!insideAny) {
                        for (ContextMenu menu : addChildCategoryMenus) {
                            if (menu != null && menu.isOpen() && inside(menu, cmx, cmy, itemH)) {
                                insideAny = true;
                                break;
                            }
                        }
                    }
                    if (!insideAny) {
                        nodeMenu.close();
                        addChildMenu.close();
                        closeAddChildCategoryMenus();
                    } else {
                        if (!handleSubmenuClick(ui, itemH)) {
                            nodeMenu.handleClick((int) ui.mouse().x, (int) ui.mouse().y, itemH);
                            closeAddChildCategoryMenus();
                            addChildMenu.close();
                        }
                    }
                }
                // Defer rendering to EditorOverlay so the menu is drawn on top of all panels
                // and not clipped to this panel's scissor rect.
                final UiRenderer deferR = r;
                final Theme deferTheme = theme;
                final int deferItemH = itemH;
                final boolean deferInteractive = interactive;
                runtime.setOverlayMenuRender(() -> {
                    if (nodeMenu.isOpen()) {
                        nodeMenu.render(deferR, deferTheme, deferItemH,
                                Theme.toArgb(deferTheme.panelBg),
                                Theme.toArgb(deferTheme.widgetHover),
                                Theme.toArgb(deferTheme.text),
                                nodeMenu.hoverIndex());
                    }
                    if (deferInteractive) {
                        renderSubmenus(deferR, deferTheme, deferItemH);
                    }
                });
            }
        }

        ui.endPanel();
    }

    private int renderSaveBranchRow(Ui ui, UiRenderer r, UiContext uiContext, Theme theme, int x, int y, int w, boolean interactive) {
        int pad = theme.design.space_sm;
        int rowH = 34;
        int fieldH = theme.design.widget_height_sm;
        int fieldY = y + (rowH - fieldH) / 2;

        int btnW = 72;
        int cancelW = 72;
        int cancelX = x + w - pad - cancelW;
        int saveX = cancelX - pad - btnW;

        int idW = 120;
        int idX = x + pad;
        int nameX = idX + idW + pad;
        int nameW = Math.max(60, saveX - pad - nameX);

        var input = interactive ? ui.input() : null;
        saveBranchSceneIdField.render(r, uiContext, input, theme, idX, fieldY, idW, fieldH, true);
        if ((saveBranchSceneIdField.text() == null || saveBranchSceneIdField.text().isEmpty()) && (uiContext == null || !saveBranchSceneIdField.isFocused(uiContext))) {
            r.drawText("scene_id", idX + 6, r.baselineForBox(fieldY, fieldH), Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.65f));
        }

        saveBranchDisplayNameField.render(r, uiContext, input, theme, nameX, fieldY, nameW, fieldH, true);
        if ((saveBranchDisplayNameField.text() == null || saveBranchDisplayNameField.text().isEmpty()) && (uiContext == null || !saveBranchDisplayNameField.isFocused(uiContext))) {
            r.drawText("Display name (optional)", nameX + 6, r.baselineForBox(fieldY, fieldH), Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.65f));
        }

        EditorUiUtil.textButton(ui, r, theme, saveX, fieldY, btnW, fieldH, "Save", true, this::commitSaveBranch);
        EditorUiUtil.textButton(ui, r, theme, cancelX, fieldY, cancelW, fieldH, "Cancel", true, () -> {
            saveBranchOpen = false;
            saveBranchError = null;
        });

        if (saveBranchFocusRequested && uiContext != null) {
            saveBranchFocusRequested = false;
            saveBranchSceneIdField.focus(uiContext);
        }

        int cursorY = y + rowH + pad;
        if (saveBranchError != null && !saveBranchError.isBlank()) {
            r.drawText(saveBranchError, x + pad, r.baselineForBox(cursorY, 18), Theme.toArgb(theme.danger));
            cursorY += 18 + pad;
        }
        return cursorY;
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

        String[] labels = new String[]{"Scene"};
        dockTabs.render(r, uiContext, input, theme, x, y, w, h, labels, 0, true, dockTabStyle);
    }

    private void renderToolbar(Ui ui, UiRenderer r, UiContext uiContext, Theme theme, int x, int y, int w, int h, boolean interactive) {
        var input = interactive ? ui.input() : null;
        int bg = Theme.toArgb(theme.windowBg);
        r.drawRect(x, y, w, h, bg);
        r.drawRect(x, y + h - 1, w, 1, Theme.toArgb(theme.headerLine));

        int pad = theme.design.space_sm;
        int btnSize = theme.design.widget_height_md;
        int gap = theme.design.space_xs;
        int rightButtons = btnSize * 3 + gap * 2 + pad;
        int searchH = theme.design.widget_height_sm;
        int searchW = Math.max(120, w - pad * 2 - rightButtons);
        int searchX = x + pad;
        int searchY = y + (h - searchH) / 2;

        filterField.render(r, uiContext, input, theme, searchX, searchY, searchW, searchH, true);
        if ((filterField.text() == null || filterField.text().isEmpty()) && (uiContext == null || !filterField.isFocused(uiContext))) {
            int hint = Theme.mulAlpha(Theme.toArgb(theme.textMuted), 0.70f);
            int sp = theme.design.space_sm;
            float iconSize = Math.min(theme.design.icon_sm, searchH - sp * 2);
            MoudIcons.drawOrFallback(r, theme, Icon.SEARCH, searchX + sp, searchY + (searchH - iconSize) * 0.5f, iconSize, hint);
            r.drawText("Filter Nodes", searchX + sp + iconSize + sp, r.baselineForBox(searchY, searchH), hint);
        }

        int btnY = y + (h - btnSize) / 2;
        int addX = x + w - pad - btnSize;
        int collapseX = addX - gap - btnSize;
        int expandX = collapseX - gap - btnSize;

        EditorUiUtil.iconButton(ui, r, theme, expandX, btnY, btnSize, btnSize, Icon.CHEVRON_DOWN, interactive, this::expandAll);
        EditorUiUtil.iconButton(ui, r, theme, collapseX, btnY, btnSize, btnSize, Icon.CHEVRON_RIGHT, interactive, this::collapseAll);
        EditorUiUtil.iconButton(ui, r, theme, addX, btnY, btnSize, btnSize, Icon.ADD, interactive, () -> {
            if (runtime.getCreateNodeDialog() == null) {
                return;
            }
            EditorState state = runtime.state();
            long parentId = state != null ? state.selectedId : 0L;
            runtime.getCreateNodeDialog().open(parentId);
        });
    }

    private void renderInlineRename(UiRenderer r, UiContext uiContext, Theme theme, UiInput input,
                                    int treeX, int treeY, int treeW, int itemH, int scrollOffset) {
        if (treeView == null || renamingNodeId < 0) {
            return;
        }
        int rowIndex = -1;
        List<TreeView.VisibleNode<SceneSnapshot.NodeSnapshot>> visNodes = treeView.getVisibleNodes();
        for (int i = 0; i < visNodes.size(); i++) {
            SceneSnapshot.NodeSnapshot sn = visNodes.get(i).node().data();
            if (sn != null && sn.nodeId() == renamingNodeId) { rowIndex = i; break; }
        }
        if (rowIndex < 0) {
            renamingNodeId = -1;
            return;
        }
        int rowY = treeY + rowIndex * itemH - scrollOffset;
        int fieldX = treeX + 32;
        int fieldW = Math.max(60, treeW - 36);
        int fieldH = itemH;
        renameFieldX = fieldX;
        renameFieldY = rowY;
        renameFieldW = fieldW;
        renameFieldH = fieldH;
        r.drawRect(fieldX, rowY, fieldW, fieldH, Theme.toArgb(theme.widgetBg));
        renameTreeField.render(r, uiContext, input, theme, fieldX, rowY, fieldW, fieldH, true);
    }

    private void saveExpandedState() {
        if (rootNode == null) return;
        expandedNodeIds.clear();
        collectExpandedIds(rootNode);
    }

    private void collectExpandedIds(TreeNode<SceneSnapshot.NodeSnapshot> node) {
        if (node == null) return;
        SceneSnapshot.NodeSnapshot snap = node.data();
        if (snap != null && node.expanded()) {
            expandedNodeIds.add(snap.nodeId());
        }
        for (TreeNode<SceneSnapshot.NodeSnapshot> child : node.children()) {
            collectExpandedIds(child);
        }
    }

    private void rebuildTree(EditorState state, String filter) {
        saveExpandedState();
        rootNode = new TreeNode<>(null);
        if (state == null || state.scene == null) {
            treeView = new TreeView<>(rootNode, 24);
            treeView.setLabelFunction(this::formatNodeLabel);
            return;
        }

        String f = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<SceneSnapshot.NodeSnapshot> roots = new ArrayList<>(state.scene.childrenOf(0L));
        roots.sort(Comparator.comparing(SceneSnapshot.NodeSnapshot::name));

        for (SceneSnapshot.NodeSnapshot root : roots) {
            TreeNode<SceneSnapshot.NodeSnapshot> node = buildTreeNode(state, root, f);
            if (node != null) {
                rootNode.addChild(node);
            }
        }

        rootNode.setExpanded(true);
        treeView = new TreeView<>(rootNode, 24);
        treeView.setLabelFunction(this::formatNodeLabel);
        treeView.setIndentStepPx(16);

        treeStyle.drawContainer = false;
        treeStyle.drawFocusRing = false;
        treeStyle.stripedRows = false;
        treeStyle.showRootNode = false;
        treeStyle.accentBarWidth = 3;
        treeView.setStyle(treeStyle);

        treeView.setMultiSelect(true);
        treeView.setOnDoubleClick(snap -> {
            if (snap != null) beginInlineRename(snap);
        });
        treeView.setCustomIconFunction(snap -> snap != null ? MoudIcons.get(snap.type()) : null);
        treeView.setCustomIconColorFunction(snap -> snap != null ? nodeTypeBadgeColor(snap.type()) : null);
        treeView.setChevronRenderer((r2, theme2, expanded, x2, y2, sz, col) ->
                MoudIcons.drawOrFallback(r2, theme2, expanded ? Icon.CHEVRON_DOWN : Icon.CHEVRON_RIGHT, x2, y2, sz, col));

        treeView.setRowSuffix((r2, theme2, node2, rowX, rowY, rowW, rowH, sel2, rowHovered2, mouseX2, suffixClicked2) -> {
            SceneSnapshot.NodeSnapshot snap = node2.data();
            if (snap == null || snap.nodeId() <= 0L) return 0;

            int btnSize  = Math.min(16, Math.max(12, rowH - 6));
            int gap      = 4;
            int margin   = 6;
            int lockX    = rowX + rowW - margin - btnSize;
            int visX     = lockX - gap - btnSize;
            int btnY     = rowY + (rowH - btnSize) / 2;

            int childCountW = 0;
            if (!node2.expanded() && !node2.children().isEmpty()) {
                String countLabel = "(" + node2.children().size() + ")";
                int countCol = Theme.toArgb(theme2.textMuted);
                float countX = visX - gap - r2.measureText(countLabel);
                r2.drawText(countLabel, countX, r2.baselineForBox(rowY, rowH), countCol);
                childCountW = (int) Math.ceil(r2.measureText(countLabel)) + gap;
            }

            boolean visible = isVisible(snap);
            boolean locked  = isLocked(snap);

            boolean visHovered  = rowHovered2 && mouseX2 >= visX  && mouseX2 < visX  + btnSize;
            boolean lockHovered = rowHovered2 && mouseX2 >= lockX && mouseX2 < lockX + btnSize;

            if (visHovered)  r2.drawRoundedRect(visX,  btnY, btnSize, btnSize, theme2.design.radius_sm, Theme.mulAlpha(Theme.toArgb(theme2.widgetHover), 0.65f));
            if (lockHovered) r2.drawRoundedRect(lockX, btnY, btnSize, btnSize, theme2.design.radius_sm, Theme.mulAlpha(Theme.toArgb(theme2.widgetHover), 0.65f));

            float iconSize  = btnSize - 4;
            float iconY2    = btnY + (btnSize - iconSize) * 0.5f;
            float visIconX  = visX  + (btnSize - iconSize) * 0.5f;
            float lockIconX = lockX + (btnSize - iconSize) * 0.5f;

            int visCol  = !visible ? Theme.toArgb(theme2.accent) : (visHovered  ? Theme.toArgb(theme2.text) : Theme.mulAlpha(Theme.toArgb(theme2.textMuted), 0.5f));
            int lockCol = locked   ? Theme.toArgb(theme2.accent) : (lockHovered ? Theme.toArgb(theme2.text) : Theme.mulAlpha(Theme.toArgb(theme2.textMuted), 0.5f));

            MoudIcons.drawOrFallback(r2, theme2, visible ? Icon.VISIBLE : Icon.INVISIBLE, visIconX, iconY2, iconSize, visCol);
            MoudIcons.drawOrFallback(r2, theme2, locked  ? Icon.LOCK    : Icon.UNLOCK,    lockIconX, iconY2, iconSize, lockCol);

            if (suffixClicked2) {
                if (mouseX2 >= visX && mouseX2 < visX + btnSize) {
                    toggleVisible(snap.nodeId(), visible);
                } else if (mouseX2 >= lockX && mouseX2 < lockX + btnSize) {
                    toggleLocked(snap.nodeId(), locked);
                }
            }

            return btnSize * 2 + gap + margin + childCountW;
        });

        treeView.setDragReorderListener((dragged, target, zone) -> {
            if (dragged == null || target == null || dragged.nodeId() == target.nodeId()) return;
            EditorState dragState = runtime.state();
            if (dragState == null || runtime.net() == null) return;

            // Prevent dropping a parent into its own descendant
            long check = target.parentId();
            while (check != 0L) {
                if (check == dragged.nodeId()) return;
                SceneSnapshot.NodeSnapshot p = dragState.scene.getNode(check);
                check = p != null ? p.parentId() : 0L;
            }

            long newParentId;
            int newIndex;

            if (zone == TreeView.DropZone.INTO) {
                newParentId = target.nodeId();
                newIndex = dragState.scene.childrenOf(newParentId).size();
            } else {
                newParentId = target.parentId();
                List<SceneSnapshot.NodeSnapshot> siblings = dragState.scene.childrenOf(newParentId);
                newIndex = 0;
                for (int i = 0; i < siblings.size(); i++) {
                    if (siblings.get(i).nodeId() == target.nodeId()) {
                        newIndex = (zone == TreeView.DropZone.BEFORE) ? i : i + 1;
                        break;
                    }
                }
            }

            long currentParentId = dragged.parentId();
            if (currentParentId == newParentId) {
                List<SceneSnapshot.NodeSnapshot> siblings = dragState.scene.childrenOf(currentParentId);
                int currentIndex = -1;
                for (int i = 0; i < siblings.size(); i++) {
                    if (siblings.get(i).nodeId() == dragged.nodeId()) { currentIndex = i; break; }
                }
                if (currentIndex >= 0) {
                    if (newIndex > currentIndex) newIndex--;
                    if (newIndex == currentIndex) return;
                }
            }
            sendOpsRecorded(List.of(new SceneOp.Reparent(dragged.nodeId(), newParentId, newIndex)));
        });
    }

    private void updateTreeStyle(Theme theme) {
        treeStyle.rowBgEven = Theme.toArgb(theme.panelBg);
        treeStyle.rowBgOdd = Theme.toArgb(theme.panelBg);
        treeStyle.rowBgHover = Theme.toArgb(theme.widgetHover);
        treeStyle.rowBgSelected = Theme.mulAlpha(Theme.toArgb(theme.widgetActive), 0.22f);
        treeStyle.textColor = Theme.toArgb(theme.text);
        treeStyle.mutedColor = Theme.toArgb(theme.textMuted);
        treeStyle.indentOverride = theme.design.icon_sm;
    }

    private TreeNode<SceneSnapshot.NodeSnapshot> buildTreeNode(EditorState state, SceneSnapshot.NodeSnapshot snapshot, String filterLower) {
        return buildTreeNode(state, snapshot, filterLower, new HashSet<>());
    }

    private TreeNode<SceneSnapshot.NodeSnapshot> buildTreeNode(EditorState state,
                                                               SceneSnapshot.NodeSnapshot snapshot,
                                                               String filterLower,
                                                               Set<Long> path) {
        if (snapshot == null) return null;
        long nodeId = snapshot.nodeId();
        if (nodeId <= 0L) return null;

        // Defensive: corrupt snapshots (e.g. self-parenting or cycles) should not crash the editor.
        if (!path.add(nodeId)) {
            TreeNode<SceneSnapshot.NodeSnapshot> node = new TreeNode<>(snapshot);
            node.setExpanded(false);
            node.setIcon(hasScript(snapshot) ? Icon.CODE : Icon.FILE);
            node.setAccentColor(nodeTypeBadgeColor(snapshot.type()));
            return node;
        }

        List<TreeNode<SceneSnapshot.NodeSnapshot>> childNodes = new ArrayList<>();
        List<SceneSnapshot.NodeSnapshot> children = new ArrayList<>(state.scene.childrenOf(snapshot.nodeId()));
        children.sort(Comparator.comparing(SceneSnapshot.NodeSnapshot::name));
        for (SceneSnapshot.NodeSnapshot child : children) {
            if (child == null || child.nodeId() == nodeId) {
                continue;
            }
            TreeNode<SceneSnapshot.NodeSnapshot> cn = buildTreeNode(state, child, filterLower, path);
            if (cn != null) {
                childNodes.add(cn);
            }
        }

        boolean matches = filterLower == null || filterLower.isEmpty()
                || (snapshot.name() != null && snapshot.name().toLowerCase(Locale.ROOT).contains(filterLower))
                || (snapshot.type() != null && snapshot.type().toLowerCase(Locale.ROOT).contains(filterLower));
        if (!matches && childNodes.isEmpty()) {
            path.remove(nodeId);
            return null;
        }

        TreeNode<SceneSnapshot.NodeSnapshot> node = new TreeNode<>(snapshot);
        for (TreeNode<SceneSnapshot.NodeSnapshot> cn : childNodes) {
            node.addChild(cn);
        }
        boolean shouldExpand = expandedNodeIds.isEmpty() || expandedNodeIds.contains(snapshot.nodeId());
        node.setExpanded(shouldExpand);
        node.setIcon(hasScript(snapshot) ? Icon.CODE : Icon.FILE);
        node.setAccentColor(nodeTypeBadgeColor(snapshot.type()));
        path.remove(nodeId);
        return node;
    }

    private static boolean hasScript(SceneSnapshot.NodeSnapshot snapshot) {
        if (snapshot == null || snapshot.properties() == null) {
            return false;
        }
        for (SceneSnapshot.Property p : snapshot.properties()) {
            if (p == null || p.key() == null) {
                continue;
            }
            if (!"script".equals(p.key())) {
                continue;
            }
            String v = p.value();
            return v != null && !v.trim().isEmpty();
        }
        return false;
    }

    private String formatNodeLabel(SceneSnapshot.NodeSnapshot snapshot) {
        if (snapshot == null) return "";
        return snapshot.name() == null ? "" : snapshot.name();
    }

    private static int nodeTypeBadgeColor(String type) {
        if (type == null) return EditorTheme.NODE_COLOR_DEFAULT;
        return switch (type) {
            case "Camera3D"                                      -> EditorTheme.NODE_COLOR_CAMERA;
            case "PlayerStart"                                   -> EditorTheme.NODE_COLOR_PLAYER;
            case "WorldEnvironment"                              -> EditorTheme.NODE_COLOR_ENVIRONMENT;
            case "CSGBlock", "CSGBox"                            -> EditorTheme.NODE_COLOR_CSG;
            case "MeshInstance3D"                                -> EditorTheme.NODE_COLOR_MESH;
            case "SceneInstance3D"                               -> EditorTheme.NODE_COLOR_SCENE_INSTANCE;
            case "OmniLight3D", "DirectionalLight3D", "SpotLight3D" -> EditorTheme.NODE_COLOR_LIGHT;
            default                                              -> EditorTheme.NODE_COLOR_DEFAULT;
        };
    }

    private void updateSelectionFromTree(EditorState state) {
        if (treeView == null || state == null) {
            return;
        }

        Set<TreeNode<SceneSnapshot.NodeSnapshot>> selected = treeView.selectedNodes();
        state.selectedIds.clear();
        if (!selected.isEmpty()) {
            TreeNode<SceneSnapshot.NodeSnapshot> node = selected.iterator().next();
            if (node.data() != null) {
                state.selectedId = node.data().nodeId();
            }
            for (TreeNode<SceneSnapshot.NodeSnapshot> tn : selected) {
                if (tn.data() != null) {
                    state.selectedIds.add(tn.data().nodeId());
                }
            }
        }
    }

    private static boolean isVisible(SceneSnapshot.NodeSnapshot node) {
        return boolProp(node, PROP_VISIBLE, true);
    }

    private static boolean isLocked(SceneSnapshot.NodeSnapshot node) {
        return boolProp(node, PROP_EDITOR_LOCKED, false) || boolProp(node, PROP_LOCKED, false);
    }

    private static boolean boolProp(SceneSnapshot.NodeSnapshot node, String key, boolean fallback) {
        if (node == null || key == null || key.isBlank()) {
            return fallback;
        }
        List<SceneSnapshot.Property> props = node.properties();
        if (props == null || props.isEmpty()) {
            return fallback;
        }
        for (SceneSnapshot.Property p : props) {
            if (p == null || p.key() == null) {
                continue;
            }
            if (!key.equals(p.key())) {
                continue;
            }
            String v = p.value();
            if (v == null) {
                return fallback;
            }
            String s = v.trim().toLowerCase(Locale.ROOT);
            if (s.isEmpty()) {
                return fallback;
            }
            if ("true".equals(s) || "1".equals(s) || "t".equals(s) || "yes".equals(s) || "y".equals(s)) {
                return true;
            }
            if ("false".equals(s) || "0".equals(s) || "f".equals(s) || "no".equals(s) || "n".equals(s)) {
                return false;
            }
            return fallback;
        }
        return fallback;
    }

    private void toggleVisible(long nodeId, boolean currentlyVisible) {
        if (nodeId <= 0L || runtime.state() == null || runtime.session() == null || runtime.net() == null) return;
        if (currentlyVisible) {
            sendOpsRecorded(List.of(new SceneOp.SetProperty(nodeId, PROP_VISIBLE, "false")));
        } else {
            sendOpsRecorded(List.of(new SceneOp.RemoveProperty(nodeId, PROP_VISIBLE)));
        }
    }

    private void toggleLocked(long nodeId, boolean currentlyLocked) {
        if (nodeId <= 0L || runtime.state() == null || runtime.session() == null || runtime.net() == null) return;
        if (currentlyLocked) {
            sendOpsRecorded(List.of(
                    new SceneOp.RemoveProperty(nodeId, PROP_EDITOR_LOCKED),
                    new SceneOp.RemoveProperty(nodeId, PROP_LOCKED)
            ));
        } else {
            sendOpsRecorded(List.of(
                    new SceneOp.SetProperty(nodeId, PROP_EDITOR_LOCKED, "true"),
                    new SceneOp.SetProperty(nodeId, PROP_LOCKED, "true")
            ));
        }
    }

    private void openNodeMenu(SceneSnapshot.NodeSnapshot node) {
        nodeMenu.clear();
        nodeMenu.addItem("Add Child…", () -> openCreateDialog(node.nodeId()));
        nodeMenu.addSeparator();
        nodeMenu.addItem("Rename", () -> beginInlineRename(node));
        Set<TreeNode<SceneSnapshot.NodeSnapshot>> multiSel = treeView != null ? treeView.selectedNodes() : Set.of();
        if (multiSel.size() > 1) {
            nodeMenu.addItem("Duplicate (" + multiSel.size() + ")", () -> duplicateSelectedNodes());
        } else {
            nodeMenu.addItem("Duplicate", () -> duplicateNode(node));
        }
        nodeMenu.addItem("Change Type…", () -> openChangeTypeDialog(node));
        nodeMenu.addSeparator();
        boolean visible = isVisible(node);
        boolean locked = isLocked(node);
        nodeMenu.addItem(visible ? "Hide" : "Show", () -> toggleVisible(node.nodeId(), visible));
        nodeMenu.addItem(locked ? "Unlock" : "Lock", () -> toggleLocked(node.nodeId(), locked));
        if (node.parentId() != 0L) {
            nodeMenu.addSeparator();
            nodeMenu.addItem("Move Up", () -> moveNode(node, -1));
            nodeMenu.addItem("Move Down", () -> moveNode(node, 1));
            nodeMenu.addItem("Move to Root", () -> moveToRoot(node));
            nodeMenu.addSeparator();
            nodeMenu.addItem("Queue free", () -> queueFree(node.nodeId()));
        }
        Set<TreeNode<SceneSnapshot.NodeSnapshot>> selectedNodes = treeView != null ? treeView.selectedNodes() : Set.of();
        if (selectedNodes.size() > 1) {
            nodeMenu.addSeparator();
            nodeMenu.addItem("Group Selection", () -> groupSelectedNodes());
        }
        nodeMenu.addSeparator();
        nodeMenu.addItem("Attach Script…", () -> attachScriptFromFile(node.nodeId()));
        nodeMenu.addItem("Copy Path", () -> copyNodePath(node));
        nodeMenu.addSeparator();
        nodeMenu.addItem("Save Branch as Scene…", () -> openSaveBranch(node));
    }

    private void groupSelectedNodes() {
        if (treeView == null) return;
        EditorState state = runtime.state();
        Session session = runtime.session();
        if (state == null || session == null) return;

        Set<TreeNode<SceneSnapshot.NodeSnapshot>> selectedNodes = treeView.selectedNodes();
        if (selectedNodes.size() < 2) return;
        ArrayList<SceneSnapshot.NodeSnapshot> selected = new ArrayList<>();
        long commonParentId = -1L;
        for (TreeNode<SceneSnapshot.NodeSnapshot> tn : selectedNodes) {
            SceneSnapshot.NodeSnapshot snap = tn != null ? tn.data() : null;
            if (snap == null || snap.parentId() == 0L) continue;
            if (commonParentId < 0L) {
                commonParentId = snap.parentId();
            } else if (snap.parentId() != commonParentId) {
                runtime.requestToast("Group Selection requires nodes with the same parent", true, 3000);
                return;
            }
            selected.add(snap);
        }
        if (commonParentId <= 0L || selected.size() < 2) {
            return;
        }

        List<SceneSnapshot.NodeSnapshot> siblings = state.scene.childrenOf(commonParentId);
        HashMap<Long, Integer> indexById = new HashMap<>();
        for (int i = 0; i < siblings.size(); i++) {
            SceneSnapshot.NodeSnapshot s = siblings.get(i);
            if (s != null) indexById.put(s.nodeId(), i);
        }

        ArrayList<EditorHistory.NodeAtIndex> nodes = new ArrayList<>();
        for (SceneSnapshot.NodeSnapshot s : selected) {
            int idx = indexById.getOrDefault(s.nodeId(), 0);
            nodes.add(new EditorHistory.NodeAtIndex(s.nodeId(), idx));
        }
        nodes.sort(Comparator.comparingInt(n -> n.index()));

        EditorHistory.GroupSelectionEntry entry = new EditorHistory.GroupSelectionEntry(commonParentId, "Group", nodes);
        runtime.history().pushEntry(entry);
        entry.redo(runtime);
    }

    private void buildAddChildMenu(SceneSnapshot.NodeSnapshot parent) {
        addChildMenu.clear();
        closeAddChildCategoryMenus();
        addChildCategoryMenus.clear();
        EditorState state = runtime.state();
        if (state == null) {
            return;
        }
        long parentId = parent == null ? 0L : parent.nodeId();
        LinkedHashMap<String, ArrayList<NodeTypeDef>> categories = new LinkedHashMap<>();
        for (String typeId : state.typeIds) {
            if (typeId == null || typeId.isBlank() || "Root".equals(typeId)) {
                continue;
            }
            NodeTypeDef def = state.typesById.get(typeId);
            String category = def == null ? "" : def.category();
            String key = (category == null || category.isBlank()) ? "Other" : category.trim();
            categories.computeIfAbsent(key, ignored -> new ArrayList<>()).add(def != null ? def : new NodeTypeDef(typeId, Map.of()));
        }

        ArrayList<String> categoryNames = new ArrayList<>(categories.keySet());
        categoryNames.sort((a, b) -> {
            String aa = a == null ? "" : a;
            String bb = b == null ? "" : b;
            if ("Other".equalsIgnoreCase(aa) && !"Other".equalsIgnoreCase(bb)) {
                return 1;
            }
            if (!"Other".equalsIgnoreCase(aa) && "Other".equalsIgnoreCase(bb)) {
                return -1;
            }
            return aa.compareToIgnoreCase(bb);
        });

        for (String category : categoryNames) {
            ArrayList<NodeTypeDef> defs = categories.get(category);
            if (defs == null || defs.isEmpty()) {
                continue;
            }
            ContextMenu catMenu = new ContextMenu();
            for (NodeTypeDef def : defs) {
                if (def == null || def.typeId() == null || def.typeId().isBlank()) {
                    continue;
                }
                String finalType = def.typeId();
                catMenu.addItem(def.uiLabel(), () -> {
                    createChildNode(parentId, finalType);
                    closeAddChildCategoryMenus();
                    addChildMenu.close();
                    nodeMenu.close();
                });
            }
            addChildCategoryMenus.add(catMenu);
            addChildMenu.addSubmenu(category, catMenu);
        }
    }

    private void closeAddChildCategoryMenus() {
        for (ContextMenu menu : addChildCategoryMenus) {
            if (menu != null) {
                menu.close();
            }
        }
    }

    private void beginInlineRename(SceneSnapshot.NodeSnapshot node) {
        if (node == null) {
            return;
        }
        nodeMenu.close();
        renamingNodeId = node.nodeId();
        renameTreeField.setText(node.name() == null ? "" : node.name());
        renameTreeField.setCursorPos(renameTreeField.text().length());
    }

    private void commitInlineRename() {
        if (renamingNodeId < 0) {
            return;
        }
        String next = renameTreeField.text();
        if (next == null) {
            next = "";
        }
        next = next.trim();
        long nodeId = renamingNodeId;
        renamingNodeId = -1;

        EditorState state = runtime.state();
        if (next.isEmpty() || state == null) {
            return;
        }
        SceneSnapshot.NodeSnapshot existing = state.scene.getNode(nodeId);
        if (existing == null || next.equals(existing.name())) {
            return;
        }
        next = uniqueSiblingName(state, existing.parentId(), nodeId, next);
        EditorNet net = runtime.net();
        Session session = runtime.session();
        if (net == null || session == null) return;
        sendOpsRecorded(List.of(new SceneOp.Rename(nodeId, next)));
        runtime.requestToast("Renamed to: " + next, false, 1500);
    }

    private static String uniqueSiblingName(EditorState state, long parentId, long selfId, String desired) {
        if (state == null || state.scene == null) return desired;
        Set<String> existing = new HashSet<>();
        for (SceneSnapshot.NodeSnapshot s : state.scene.childrenOf(parentId)) {
            if (s != null && s.name() != null && s.nodeId() != selfId) existing.add(s.name());
        }
        if (!existing.contains(desired)) return desired;
        int n = 2;
        while (existing.contains(desired + " (" + n + ")")) n++;
        return desired + " (" + n + ")";
    }

    private void openSaveBranch(SceneSnapshot.NodeSnapshot node) {
        nodeMenu.close();
        closeAddChildCategoryMenus();
        addChildMenu.close();
        saveBranchRootId = node != null ? node.nodeId() : 0L;
        saveBranchOpen = true;
        saveBranchFocusRequested = true;
        saveBranchError = null;

        String suggested = node != null ? normalizeSceneId(node.name()) : "";
        if (suggested == null || !isValidSceneId(suggested)) {
            suggested = "branch_" + saveBranchRootId;
        }
        saveBranchSceneIdField.setText(suggested);
        saveBranchSceneIdField.setCursorPos(saveBranchSceneIdField.text().length());
        saveBranchDisplayNameField.setText(node != null ? node.name() : "");
        saveBranchDisplayNameField.setCursorPos(saveBranchDisplayNameField.text().length());
    }

    private void commitSaveBranch() {
        EditorState state = runtime.state();
        if (state == null || state.scene == null) {
            saveBranchError = "No scene loaded";
            return;
        }

        String sid = normalizeSceneId(saveBranchSceneIdField.text());
        if (!isValidSceneId(sid)) {
            saveBranchError = "Invalid id (use [a-z0-9_-], max 64 chars)";
            return;
        }

        SceneSnapshot.NodeSnapshot selectedRoot = state.scene.getNode(saveBranchRootId);
        if (selectedRoot == null) {
            saveBranchError = "Branch root not found";
            return;
        }

        List<SceneFile.NodeEntry> nodes = buildBranchSceneFileNodes(state, selectedRoot);
        if (nodes.isEmpty()) {
            saveBranchError = "Branch is empty";
            return;
        }

        String displayName = saveBranchDisplayNameField.text();
        SceneFile file = new SceneFile(SceneFile.FORMAT_V1, sid, displayName, nodes);
        String json = GSON.toJson(file);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        if (!uploadSceneFile(sid, bytes)) {
            return;
        }

        saveBranchOpen = false;
        saveBranchError = null;
    }

    private static List<SceneFile.NodeEntry> buildBranchSceneFileNodes(EditorState state, SceneSnapshot.NodeSnapshot selectedRoot) {
        if (state == null || state.scene == null || selectedRoot == null) {
            return List.of();
        }

        boolean includeRoot = selectedRoot.parentId() != 0L;
        boolean includeRuntimePlayers = isRuntimePlayerNode(selectedRoot);

        List<SceneSnapshot.NodeSnapshot> roots = includeRoot
                ? List.of(selectedRoot)
                : state.scene.childrenOf(selectedRoot.nodeId());

        ArrayList<SceneSnapshot.NodeSnapshot> branchNodes = new ArrayList<>();
        HashSet<Long> includedIds = new HashSet<>();
        collectBranchNodes(state, roots, includeRuntimePlayers, branchNodes, includedIds);
        if (branchNodes.isEmpty()) {
            return List.of();
        }

        long selectedRootId = selectedRoot.nodeId();
        ArrayList<SceneFile.NodeEntry> out = new ArrayList<>(branchNodes.size());
        for (SceneSnapshot.NodeSnapshot node : branchNodes) {
            out.add(toSceneFileNodeEntry(node, selectedRootId, includeRoot, includedIds));
        }
        return List.copyOf(out);
    }

    private static void collectBranchNodes(EditorState state,
                                           List<SceneSnapshot.NodeSnapshot> roots,
                                           boolean includeRuntimePlayers,
                                           List<SceneSnapshot.NodeSnapshot> out,
                                           Set<Long> includedIds) {
        if (state == null || state.scene == null || roots == null || roots.isEmpty()) {
            return;
        }

        ArrayDeque<SceneSnapshot.NodeSnapshot> stack = new ArrayDeque<>(roots.size());
        for (int i = roots.size() - 1; i >= 0; i--) {
            SceneSnapshot.NodeSnapshot node = roots.get(i);
            if (node != null) {
                stack.push(node);
            }
        }

        while (!stack.isEmpty()) {
            SceneSnapshot.NodeSnapshot node = stack.pop();
            if (!includeRuntimePlayers && isRuntimePlayerNode(node)) {
                continue;
            }
            long id = node.nodeId();
            if (!includedIds.add(id)) {
                continue;
            }
            out.add(node);

            List<SceneSnapshot.NodeSnapshot> children = state.scene.childrenOf(id);
            for (int i = children.size() - 1; i >= 0; i--) {
                SceneSnapshot.NodeSnapshot child = children.get(i);
                if (child != null) {
                    stack.push(child);
                }
            }
        }
    }

    private static SceneFile.NodeEntry toSceneFileNodeEntry(SceneSnapshot.NodeSnapshot node,
                                                           long selectedRootId,
                                                           boolean includeRoot,
                                                           Set<Long> includedIds) {
        long parent = remapParentId(node, selectedRootId, includeRoot, includedIds);
        String name = exportNodeName(node);
        String type = node.type() == null || node.type().isBlank() ? "Node" : node.type();
        Map<String, String> props = exportNodeProps(node);
        return new SceneFile.NodeEntry(node.nodeId(), parent, name, type, props);
    }

    private static long remapParentId(SceneSnapshot.NodeSnapshot node,
                                      long selectedRootId,
                                      boolean includeRoot,
                                      Set<Long> includedIds) {
        if (node == null) {
            return 0L;
        }
        long parent = node.parentId();
        if (includeRoot && node.nodeId() == selectedRootId) {
            return 0L;
        }
        if (!includeRoot && parent == selectedRootId) {
            return 0L;
        }
        if (includedIds != null && includedIds.contains(parent)) {
            return parent;
        }
        return 0L;
    }

    private static String exportNodeName(SceneSnapshot.NodeSnapshot node) {
        if (node == null) {
            return "Node";
        }
        String name = node.name() == null ? "" : node.name().trim();
        if (!name.isEmpty()) {
            return name;
        }
        String type = node.type() == null || node.type().isBlank() ? "Node" : node.type();
        return type + "_" + node.nodeId();
    }

    private static Map<String, String> exportNodeProps(SceneSnapshot.NodeSnapshot node) {
        if (node == null || node.properties() == null || node.properties().isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> props = new LinkedHashMap<>();
        for (SceneSnapshot.Property prop : node.properties()) {
            if (prop == null || prop.key() == null || prop.key().isBlank() || prop.value() == null) {
                continue;
            }
            if ("@type".equals(prop.key())) {
                continue;
            }
            props.put(prop.key(), prop.value());
        }
        return Map.copyOf(props);
    }

    private boolean uploadSceneFile(String sceneId, byte[] bytes) {
        AssetsClient assets = runtime.assets();
        Session session = runtime.session();
        if (assets == null || session == null) {
            saveBranchError = "Not connected";
            return false;
        }

        ResPath path;
        try {
            path = new ResPath("res://scenes/" + sceneId + ".moud.scene");
        } catch (Exception e) {
            saveBranchError = "Invalid path: " + e.getMessage();
            return false;
        }

        assets.addListener(new AssetsClient.Listener() {
            @Override
            public void onUploadAck(AssetUploadAck ack) {
                if (ack == null || !path.equals(ack.path())) {
                    return;
                }
                if (ack.status() == AssetTransferStatus.OK || ack.status() == AssetTransferStatus.ALREADY_PRESENT) {
                    assets.requestManifest(session);
                }
                assets.removeListener(this);
            }
        });

        assets.upload(session, path, bytes, AssetType.BINARY);
        return true;
    }

    private void createChildNode(long parentId, String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return;
        }
        EditorState state = runtime.state();
        Session session = runtime.session();
        if (state == null || session == null) {
            return;
        }
        if ("PlayerStart".equals(typeId) && sceneHasPlayerStart(state)) {
            runtime.requestToast("A PlayerStart already exists. Only one is used (first found in scene).", true, 4000);
            return;
        }
        EditorHistory.CreateNodeEntry entry = new EditorHistory.CreateNodeEntry(parentId, typeId, typeId, List.of(), true);
        runtime.history().pushEntry(entry);
        entry.redo(runtime);
    }

    private static boolean sceneHasPlayerStart(EditorState state) {
        if (state == null || state.scene == null || state.scene.nodes() == null) {
            return false;
        }
        for (SceneSnapshot.NodeSnapshot node : state.scene.nodes()) {
            if (node != null && "PlayerStart".equals(node.type())) {
                return true;
            }
        }
        return false;
    }

    private static String uniqueChildName(EditorState state, long parentId, String typeId) {
        if (state == null || state.scene == null) return typeId;
        Set<String> existing = new HashSet<>();
        for (SceneSnapshot.NodeSnapshot s : state.scene.childrenOf(parentId)) {
            if (s != null && s.name() != null) existing.add(s.name());
        }
        if (!existing.contains(typeId)) return typeId;
        int n = 2;
        while (existing.contains(typeId + n)) n++;
        return typeId + n;
    }

    private void duplicateNode(SceneSnapshot.NodeSnapshot node) {
        if (node == null) {
            return;
        }
        nodeMenu.close();
        addChildMenu.close();
        closeAddChildCategoryMenus();
        EditorState state = runtime.state();
        Session session = runtime.session();
        if (state == null || session == null) {
            return;
        }
        duplicateSubtree(state, node, 0.0f);
    }

    private void duplicateSelectedNodes() {
        if (treeView == null) return;
        nodeMenu.close();
        addChildMenu.close();
        closeAddChildCategoryMenus();
        EditorState state = runtime.state();
        Session session = runtime.session();
        if (state == null || session == null) return;
        Set<Long> selectedIds = new HashSet<>();
        for (TreeNode<SceneSnapshot.NodeSnapshot> tn : treeView.selectedNodes()) {
            SceneSnapshot.NodeSnapshot snap = tn != null ? tn.data() : null;
            if (snap != null) {
                selectedIds.add(snap.nodeId());
            }
        }
        for (long nodeId : topLevelSelection(state, selectedIds)) {
            SceneSnapshot.NodeSnapshot snap = state.scene.getNode(nodeId);
            if (snap != null) {
                duplicateSubtree(state, snap, 0.0f);
            }
        }
    }

    private void duplicateNodeWithOffset(SceneSnapshot.NodeSnapshot node) {
        if (node == null) {
            return;
        }
        nodeMenu.close();
        addChildMenu.close();
        closeAddChildCategoryMenus();
        EditorState state = runtime.state();
        Session session = runtime.session();
        if (state == null || session == null) {
            return;
        }

        float offset = runtime != null ? runtime.gridSnapStep() : 0.5f;
        if ("CSGBlock".equals(node.type())) {
            offset = Math.max(1.0f, Math.round(offset));
        } else {
            offset = Math.max(0.25f, offset);
        }
        duplicateSubtree(state, node, offset);
    }

    private void duplicateSubtree(EditorState state, SceneSnapshot.NodeSnapshot node, float offset) {
        if (state == null || node == null) {
            return;
        }

        String baseName = node.name() == null ? "Node" : node.name();
        String nameHint = baseName + "_copy";
        var spec = buildCloneSpec(state, node.nodeId(), true, offset);
        if (spec == null) {
            return;
        }
        var entry = new EditorHistory.DuplicateSubtreeEntry(node.parentId(), nameHint, spec, true);
        runtime.history().pushEntry(entry);
        entry.redo(runtime);
    }

    private EditorHistory.DuplicateSubtreeEntry.CloneSpec buildCloneSpec(EditorState state,
                                                                         long nodeId,
                                                                         boolean isRoot,
                                                                         float rootOffset) {
        if (state == null || state.scene == null || nodeId <= 0L) {
            return null;
        }
        return buildCloneSpec(state, nodeId, isRoot, rootOffset, new HashSet<>());
    }

    private EditorHistory.DuplicateSubtreeEntry.CloneSpec buildCloneSpec(EditorState state,
                                                                         long nodeId,
                                                                         boolean isRoot,
                                                                         float rootOffset,
                                                                         Set<Long> visiting) {
        if (state == null || state.scene == null || nodeId <= 0L) {
            return null;
        }
        if (!visiting.add(nodeId)) {
            return null;
        }
        try {
            SceneSnapshot.NodeSnapshot node = state.scene.getNode(nodeId);
            if (node == null) {
                return null;
            }

            String name = node.name();
            String typeId = node.type();

            ArrayList<Map.Entry<String, String>> props = new ArrayList<>();
            if (node.properties() != null) {
                for (SceneSnapshot.Property p : node.properties()) {
                    if (p == null || p.key() == null || p.value() == null) {
                        continue;
                    }
                    String key = p.key();
                    if ("@type".equals(key)) {
                        continue;
                    }
                    String value = p.value();
                    if (isRoot && rootOffset != 0.0f && ("x".equals(key) || "z".equals(key))) {
                        float v = ParseUtils.parseFloat(value, 0.0f);
                        value = ParseUtils.trimFloat(v + rootOffset);
                    }
                    props.add(Map.entry(key, value));
                }
            }

            ArrayList<EditorHistory.DuplicateSubtreeEntry.CloneSpec> children = new ArrayList<>();
            for (SceneSnapshot.NodeSnapshot child : state.scene.childrenOf(nodeId)) {
                if (child == null || child.nodeId() <= 0L) {
                    continue;
                }
                var childSpec = buildCloneSpec(state, child.nodeId(), false, 0.0f, visiting);
                if (childSpec != null) {
                    children.add(childSpec);
                }
            }

            return new EditorHistory.DuplicateSubtreeEntry.CloneSpec(name, typeId, props, children);
        } finally {
            visiting.remove(nodeId);
        }
    }

    private static List<Long> topLevelSelection(EditorState state, Set<Long> selectedIds) {
        if (state == null || state.scene == null || selectedIds == null || selectedIds.isEmpty()) {
            return List.of();
        }

        ArrayList<Long> out = new ArrayList<>(selectedIds.size());
        for (long id : selectedIds) {
            if (id <= 0L) {
                continue;
            }
            SceneSnapshot.NodeSnapshot node = state.scene.getNode(id);
            if (node == null) {
                continue;
            }
            boolean hasSelectedAncestor = false;
            long parent = node.parentId();
            while (parent > 0L) {
                if (selectedIds.contains(parent)) {
                    hasSelectedAncestor = true;
                    break;
                }
                SceneSnapshot.NodeSnapshot p = state.scene.getNode(parent);
                parent = p != null ? p.parentId() : 0L;
            }
            if (!hasSelectedAncestor) {
                out.add(id);
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private void moveNode(SceneSnapshot.NodeSnapshot node, int direction) {
        if (node == null || node.parentId() == 0L) {
            return;
        }
        nodeMenu.close();
        EditorState state = runtime.state();
        if (state == null) {
            return;
        }
        List<SceneSnapshot.NodeSnapshot> siblings = state.scene.childrenOf(node.parentId());
        int currentIndex = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).nodeId() == node.nodeId()) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex < 0) {
            return;
        }
        int newIndex = currentIndex + direction;
        if (newIndex < 0 || newIndex >= siblings.size()) {
            return;
        }
        sendOpsRecorded(List.of(new SceneOp.Reparent(node.nodeId(), node.parentId(), newIndex)));
    }

    private static String normalizeSceneId(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
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

    private static boolean inside(ContextMenu menu, int mx, int my, int itemHeight) {
        if (menu == null || !menu.isOpen()) {
            return false;
        }
        int x = menu.x();
        int y = menu.y();
        int w = menu.lastWidth() > 0 ? menu.lastWidth() : 200;
        int itemH = Math.max(1, itemHeight);
        int h = menu.lastHeight() > 0 ? menu.lastHeight() : menu.items().size() * itemH;
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private void syncSubmenus(Ui ui, Theme theme, int itemH) {
        if (runtime != null && runtime.uiBlocked()) {
            return;
        }
        if (ui == null || ui.input() == null || theme == null) {
            return;
        }
        int mx = (int) ui.mouse().x;
        int my = (int) ui.mouse().y;

        int hover = nodeMenu.hoverIndex();
        boolean nodeHoverSubmenu = false;
        if (hover >= 0 && hover < nodeMenu.items().size()) {
            ContextMenu.MenuItem item = nodeMenu.items().get(hover);
            if (item != null && item.submenu() != null) {
                nodeHoverSubmenu = true;
                ContextMenu submenu = item.submenu();
                int sx = nodeMenu.x() + nodeMenu.lastWidth() - 2;
                int sy = nodeMenu.y() + hover * itemH;
                EditorUiUtil.openMenuClamped(submenu, runtime, sx, sy);
                submenu.updateFromInput(ui.input(), theme, itemH);
                EditorUiUtil.clampOpenMenuToScreen(submenu, runtime);
            }
        }

        boolean insideAddChild = addChildMenu.isOpen() && inside(addChildMenu, mx, my, itemH);
        boolean insideCategory = false;
        for (ContextMenu menu : addChildCategoryMenus) {
            if (menu != null && menu.isOpen() && inside(menu, mx, my, itemH)) {
                insideCategory = true;
                break;
            }
        }
        if (!nodeHoverSubmenu && !insideAddChild && !insideCategory) {
            closeAddChildCategoryMenus();
            addChildMenu.close();
            return;
        }

        if (addChildMenu.isOpen()) {
            if (insideAddChild) {
                addChildMenu.updateFromInput(ui.input(), theme, itemH);
                EditorUiUtil.clampOpenMenuToScreen(addChildMenu, runtime);
            }

            int catHover = addChildMenu.hoverIndex();
            boolean hoveringCategory = false;
            if (catHover >= 0 && catHover < addChildMenu.items().size()) {
                ContextMenu.MenuItem item = addChildMenu.items().get(catHover);
                if (item != null && item.submenu() != null) {
                    hoveringCategory = true;
                    ContextMenu submenu = item.submenu();
                    int sx = addChildMenu.x() + addChildMenu.lastWidth() - 2;
                    int sy = addChildMenu.y() + catHover * itemH;
                    EditorUiUtil.openMenuClamped(submenu, runtime, sx, sy);
                    submenu.updateFromInput(ui.input(), theme, itemH);
                    EditorUiUtil.clampOpenMenuToScreen(submenu, runtime);
                }
            }
            if (!hoveringCategory && !insideCategory) {
                closeAddChildCategoryMenus();
            }
        }
    }

    private void renderSubmenus(UiRenderer r, Theme theme, int itemH) {
        if (r == null || theme == null) {
            return;
        }
        if (addChildMenu.isOpen()) {
            addChildMenu.render(r, theme, itemH,
                    Theme.toArgb(theme.panelBg),
                    Theme.toArgb(theme.widgetHover),
                    Theme.toArgb(theme.text),
                    addChildMenu.hoverIndex());
        }
        for (ContextMenu menu : addChildCategoryMenus) {
            if (menu == null || !menu.isOpen()) {
                continue;
            }
            menu.render(r, theme, itemH,
                    Theme.toArgb(theme.panelBg),
                    Theme.toArgb(theme.widgetHover),
                    Theme.toArgb(theme.text),
                    menu.hoverIndex());
        }
    }

    private boolean handleSubmenuClick(Ui ui, int itemH) {
        if (ui == null || ui.input() == null) {
            return false;
        }
        int mx = (int) ui.mouse().x;
        int my = (int) ui.mouse().y;

        for (ContextMenu menu : addChildCategoryMenus) {
            if (menu == null || !menu.isOpen()) {
                continue;
            }
            if (!inside(menu, mx, my, itemH)) {
                continue;
            }
            boolean handled = menu.handleClick(mx, my, itemH);
            if (handled) {
                nodeMenu.close();
                addChildMenu.close();
                closeAddChildCategoryMenus();
            }
            return handled;
        }

        if (!addChildMenu.isOpen() || !inside(addChildMenu, mx, my, itemH)) {
            return false;
        }
        boolean handled = addChildMenu.handleClick(mx, my, itemH);
        if (handled) {
            nodeMenu.close();
            addChildMenu.close();
            closeAddChildCategoryMenus();
        }
        return handled;
    }

    private void queueFree(long nodeId) {
        nodeMenu.close();
        addChildMenu.close();
        closeAddChildCategoryMenus();
        EditorState state = runtime.state();
        Session session = runtime.session();
        EditorNet net = runtime.net();
        if (state == null || session == null || net == null) return;
        SceneSnapshot.NodeSnapshot delNode = state.scene.getNode(nodeId);
        runtime.history().clearRedo();
        net.sendOps(session, state, List.of(new SceneOp.QueueFree(nodeId)));
        if (state.selectedId == nodeId) {
            state.selectedId = 0L;
        }
        if (delNode != null) {
            runtime.requestToast("Deleted: " + delNode.name(), false, 1500);
        }
    }

    /** Sends ops, records undo/redo history, and returns the batch ID. */
    private long sendOpsRecorded(List<SceneOp> ops) {
        EditorState state = runtime.state();
        Session session = runtime.session();
        EditorNet net = runtime.net();
        if (state == null || session == null || net == null) return 0L;
        List<SceneOp> inverseOps = suppressHistory ? List.of()
                : EditorHistory.buildInverseOps(state.scene, ops);
        long batchId = net.sendOpsWithBatchId(session, state, ops);
        if (!suppressHistory && !inverseOps.isEmpty()) {
            runtime.history().push(inverseOps, ops);
        }
        return batchId;
    }

    public void performUndo() {
        runtime.history().undo(runtime);
    }

    public void performRedo() {
        runtime.history().redo(runtime);
    }

    private void selectAll() {
        var visible = treeView.getVisibleNodes();
        if (visible.isEmpty()) return;
        treeView.selectedNodes().clear();
        for (var vn : visible) {
            if (vn.node().data() != null) {
                vn.node().setSelected(true);
                treeView.selectedNodes().add(vn.node());
            }
        }
        EditorState state = runtime.state();
        if (state != null && !visible.isEmpty() && visible.get(0).node().data() != null) {
            state.selectedId = visible.get(0).node().data().nodeId();
        }
    }

    private void copySelectedNode() {
        EditorState state = runtime.state();
        if (state == null) return;
        SceneSnapshot.NodeSnapshot selected = state.scene.getNode(state.selectedId);
        if (selected == null) return;
        clipboard.clear();
        clipboard.add(new ClipboardEntry(
                selected.name() == null ? "Node" : selected.name(),
                selected.type() == null ? "Node" : selected.type(),
                selected.properties() != null ? List.copyOf(selected.properties()) : List.of()));
        runtime.requestToast("Copied: " + selected.name(), false, 1500);
    }

    private void pasteNodes() {
        pasteNodesInternal(false);
    }

    private void pasteAsSibling() {
        pasteNodesInternal(true);
    }

    private void pasteNodesInternal(boolean asSibling) {
        if (clipboard.isEmpty()) return;
        EditorState state = runtime.state();
        Session session = runtime.session();
        if (state == null || session == null) return;
        long parentId;
        SceneSnapshot.NodeSnapshot sel = state.scene.getNode(state.selectedId);
        if (asSibling && sel != null && sel.parentId() != 0L) {
            parentId = sel.parentId();
        } else if (sel != null) {
            parentId = sel.nodeId();
        } else {
            parentId = 0L;
        }
        long finalParentId = parentId;
        long pendingCut = cutNodeId;
        cutNodeId = -1;
        for (ClipboardEntry entry : clipboard) {
            ArrayList<Map.Entry<String, String>> props = new ArrayList<>();
            if (entry.properties() != null) {
                for (SceneSnapshot.Property p : entry.properties()) {
                    if (p == null || p.key() == null || p.value() == null) continue;
                    if ("@type".equals(p.key())) continue;
                    props.add(Map.entry(p.key(), p.value()));
                }
            }

            EditorHistory.CreateNodeEntry hist = new EditorHistory.CreateNodeEntry(finalParentId, entry.name(), entry.type(), props, true);
            runtime.history().pushEntry(hist);
            hist.redo(runtime);
        }
        if (pendingCut > 0L) {
            queueFree(pendingCut);
        }
        int count = clipboard.size();
        runtime.requestToast("Pasted " + count + " node" + (count == 1 ? "" : "s"), false, 1500);
    }

    private void cutSelectedNode() {
        EditorState state = runtime.state();
        if (state == null) return;
        SceneSnapshot.NodeSnapshot selected = state.scene.getNode(state.selectedId);
        if (selected == null || selected.parentId() == 0L) return;
        copySelectedNode();
        cutNodeId = selected.nodeId();
        runtime.requestToast("Cut: " + selected.name(), false, 1500);
    }

    private void copyNodePath(SceneSnapshot.NodeSnapshot node) {
        if (node == null) return;
        nodeMenu.close();
        String path = buildNodePath(node);
        if (lastUiContext != null) {
            lastUiContext.clipboard().setText(path);
        }
        runtime.requestToast("Copied path: " + path, false, 1500);
    }

    private String buildNodePath(SceneSnapshot.NodeSnapshot node) {
        EditorState state = runtime.state();
        if (state == null || node == null) return "";
        ArrayList<String> parts = new ArrayList<>();
        SceneSnapshot.NodeSnapshot current = node;
        while (current != null && current.parentId() != 0L) {
            parts.add(current.name() != null ? current.name() : "?");
            current = state.scene.getNode(current.parentId());
        }
        if (current != null) {
            parts.add(current.name() != null ? current.name() : "?");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = parts.size() - 1; i >= 0; i--) {
            if (sb.length() > 0) sb.append('/');
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private void jumpToFirstFilterMatch(String filterLower) {
        EditorState state = runtime.state();
        if (state == null || state.scene == null || filterLower == null || filterLower.isBlank()) return;
        for (SceneSnapshot.NodeSnapshot node : state.scene.nodes()) {
            if (node == null) continue;
            String name = node.name() == null ? "" : node.name().toLowerCase(java.util.Locale.ROOT);
            String type = node.type() == null ? "" : node.type().toLowerCase(java.util.Locale.ROOT);
            if (name.contains(filterLower) || type.contains(filterLower)) {
                state.selectedId = node.nodeId();
                runtime.requestFrameSelected();
                return;
            }
        }
    }

    private void navigateTree(boolean down) {
        if (treeView == null) return;
        List<TreeView.VisibleNode<SceneSnapshot.NodeSnapshot>> visible = treeView.getVisibleNodes();
        if (visible.isEmpty()) return;
        EditorState state = runtime.state();
        if (state == null) return;
        int selectedIdx = -1;
        for (int i = 0; i < visible.size(); i++) {
            SceneSnapshot.NodeSnapshot sn = visible.get(i).node().data();
            if (sn != null && sn.nodeId() == state.selectedId) { selectedIdx = i; break; }
        }
        int nextIdx = down
                ? Math.min(visible.size() - 1, selectedIdx + 1)
                : Math.max(0, selectedIdx < 0 ? 0 : selectedIdx - 1);
        SceneSnapshot.NodeSnapshot target = visible.get(nextIdx).node().data();
        if (target != null) {
            clearTreeSelection();
            visible.get(nextIdx).node().setSelected(true);
            treeView.selectedNodes().add(visible.get(nextIdx).node());
            state.selectedId = target.nodeId();
        }
    }

    private void navigateTreeLeft() {
        if (treeView == null) return;
        List<TreeView.VisibleNode<SceneSnapshot.NodeSnapshot>> visible = treeView.getVisibleNodes();
        if (visible.isEmpty()) return;
        EditorState state = runtime.state();
        if (state == null) return;
        for (int i = 0; i < visible.size(); i++) {
            var vn = visible.get(i);
            SceneSnapshot.NodeSnapshot sn = vn.node().data();
            if (sn == null || sn.nodeId() != state.selectedId) continue;
            if (!vn.node().children().isEmpty() && vn.node().expanded()) {
                vn.node().setExpanded(false);
            } else if (sn.parentId() != 0L) {
                SceneSnapshot.NodeSnapshot parent = state.scene.getNode(sn.parentId());
                if (parent != null) {
                    clearTreeSelection();
                    state.selectedId = parent.nodeId();
                    selectNodeInTree(parent.nodeId(), visible);
                }
            }
            return;
        }
    }

    private void navigateTreeRight() {
        if (treeView == null) return;
        List<TreeView.VisibleNode<SceneSnapshot.NodeSnapshot>> visible = treeView.getVisibleNodes();
        if (visible.isEmpty()) return;
        EditorState state = runtime.state();
        if (state == null) return;
        for (int i = 0; i < visible.size(); i++) {
            var vn = visible.get(i);
            SceneSnapshot.NodeSnapshot sn = vn.node().data();
            if (sn == null || sn.nodeId() != state.selectedId) continue;
            if (!vn.node().children().isEmpty() && !vn.node().expanded()) {
                vn.node().setExpanded(true);
            } else if (i + 1 < visible.size()) {
                SceneSnapshot.NodeSnapshot next = visible.get(i + 1).node().data();
                if (next != null) {
                    clearTreeSelection();
                    visible.get(i + 1).node().setSelected(true);
                    treeView.selectedNodes().add(visible.get(i + 1).node());
                    state.selectedId = next.nodeId();
                }
            }
            return;
        }
    }

    private void selectNodeInTree(long nodeId, List<TreeView.VisibleNode<SceneSnapshot.NodeSnapshot>> visible) {
        for (var vn : visible) {
            SceneSnapshot.NodeSnapshot sn = vn.node().data();
            if (sn != null && sn.nodeId() == nodeId) {
                vn.node().setSelected(true);
                treeView.selectedNodes().add(vn.node());
                return;
            }
        }
    }

    private void clearTreeSelection() {
        if (treeView == null) return;
        for (TreeNode<SceneSnapshot.NodeSnapshot> tn : treeView.selectedNodes()) {
            tn.setSelected(false);
        }
        treeView.selectedNodes().clear();
    }

    private void expandAll() {
        if (rootNode == null) return;
        setExpandedRecursive(rootNode, true);
    }

    private void collapseAll() {
        if (rootNode == null) return;
        setExpandedRecursive(rootNode, false);
        rootNode.setExpanded(true);
    }

    private static void setExpandedRecursive(TreeNode<?> node, boolean expanded) {
        node.setExpanded(expanded);
        for (TreeNode<?> child : node.children()) {
            setExpandedRecursive(child, expanded);
        }
    }

    private void openCreateDialog(long parentNodeId) {
        if (runtime.getCreateNodeDialog() == null) {
            return;
        }
        nodeMenu.close();
        closeAddChildCategoryMenus();
        addChildMenu.close();
        runtime.getCreateNodeDialog().open(parentNodeId);
    }

    private void moveToRoot(SceneSnapshot.NodeSnapshot node) {
        if (node == null || node.parentId() == 0L) return;
        nodeMenu.close();
        EditorState state = runtime.state();
        if (state == null) return;
        int index = state.scene.childrenOf(0L).size();
        sendOpsRecorded(List.of(new SceneOp.Reparent(node.nodeId(), 0L, index)));
    }

    private void attachScriptFromFile(long nodeId) {
        nodeMenu.close();
        EditorState state = runtime.state();
        EditorNet net = runtime.net();
        Session session = runtime.session();
        if (state == null || net == null || session == null) {
            runtime.requestToast("Cannot attach: not connected", true, 3500);
            return;
        }
        try {
            String osPath = TinyFileDialogs.tinyfd_openFileDialog(
                    "Attach Script (.js)", "", null, "JavaScript (.js)", false);
            if (osPath == null || osPath.isBlank()) return;
            File file = new File(osPath);
            if (!file.exists() || !file.isFile()) {
                runtime.requestToast("Script file not found", true, 4500);
                return;
            }
            String filename = file.getName();
            if (filename == null || filename.isBlank()) {
                runtime.requestToast("Invalid filename", true, 4500);
                return;
            }
            if (!filename.toLowerCase(Locale.ROOT).endsWith(".js")) {
                filename = filename + ".js";
            }
            String scriptPath = "res://scripts/" + filename;
            try {
                new ResPath(scriptPath);
            } catch (Exception ignored) {
                scriptPath = "res://scripts/node_" + nodeId + ".js";
            }
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            net.writeScriptFile(session, state, scriptPath, content);
            sendOpsRecorded(List.of(new SceneOp.SetProperty(nodeId, "script", scriptPath)));
            runtime.requestToast("Attached script: " + scriptPath, false, 2500);
        } catch (Exception e) {
            String msg = e.getMessage();
            runtime.requestToast("Attach failed" + (msg == null || msg.isBlank() ? "" : ": " + msg), true, 6000);
        }
    }

    private void openChangeTypeDialog(SceneSnapshot.NodeSnapshot node) {
        if (node == null) return;
        nodeMenu.close();
        EditorState state = runtime.state();
        Session session = runtime.session();
        if (state == null || session == null) return;
        if (runtime.getCreateNodeDialog() == null) return;
        long nodeId = node.nodeId();
        runtime.getCreateNodeDialog().open(node.parentId());
        runtime.getCreateNodeDialog().setOnTypeSelected(typeId -> {
            if (typeId != null && !typeId.isBlank()) {
                sendOpsRecorded(List.of(new SceneOp.SetProperty(nodeId, "@type", typeId)));
            }
        });
    }

    private static boolean isRuntimePlayerNode(SceneSnapshot.NodeSnapshot node) {
        if (node == null) {
            return false;
        }
        return "PlayerStart".equals(node.type());
    }
}
