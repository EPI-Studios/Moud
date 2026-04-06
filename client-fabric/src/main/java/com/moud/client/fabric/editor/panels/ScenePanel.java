package com.moud.client.fabric.editor.panels;

import com.miry.platform.InputConstants;
import com.miry.ui.UiContext;
import com.miry.ui.PanelContext;
import com.miry.ui.Ui;
import com.miry.ui.dnd.DropTarget;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.event.TextInputEvent;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.theme.Icon;
import com.miry.ui.widgets.ContextMenu;
import com.miry.ui.widgets.StripTabs;
import com.miry.ui.widgets.TextField;
import com.miry.ui.widgets.TreeView;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.theme.EditorTheme;
import com.moud.client.fabric.editor.util.EditorDnD;
import com.moud.client.fabric.editor.util.EditorUiUtil;
import com.moud.client.fabric.render.MoudIcons;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ScenePanel extends Panel {
    private final EditorRuntime runtime;
    private final SceneBranchExporter sceneBranchExporter;
    private final SceneNodeOps sceneNodeOps;

    private final StripTabs dockTabs = new StripTabs();
    private final StripTabs.Style dockTabStyle = new StripTabs.Style();

    private final TextField filterField = new TextField();
    private final SceneNodeMenu sceneNodeMenu;
    private final SceneTreeController sceneTreeController;
    private String lastFilter = "";

    private long lastRev = Long.MIN_VALUE;
    private String lastSceneId = "";

    private long filterChangedAtMs;
    private boolean renameJustCancelled;

    private UiContext lastUiContext;
    private final SceneNodeClipboard sceneNodeClipboard;

    public ScenePanel(EditorRuntime runtime) {
        super("");
        this.runtime = runtime;
        this.sceneBranchExporter = new SceneBranchExporter(runtime);
        this.sceneNodeClipboard = new SceneNodeClipboard(runtime);
        this.sceneNodeOps = new SceneNodeOps(runtime);
        this.sceneNodeMenu = new SceneNodeMenu(runtime, sceneNodeOps, sceneNodeClipboard, sceneBranchExporter);
        this.sceneTreeController = new SceneTreeController(runtime);
        this.sceneTreeController.setSceneNodeOps(sceneNodeOps);
        this.sceneTreeController.setSceneNodeMenu(sceneNodeMenu);
        this.sceneNodeMenu.setBeginInlineRename(sceneTreeController::beginInlineRename);
        this.sceneBranchExporter.setMenus(sceneNodeMenu.nodeMenu, sceneNodeMenu.addChildMenu, sceneNodeMenu::closeAddChildCategoryMenus);
        this.sceneNodeOps.setMenus(sceneNodeMenu.nodeMenu, sceneNodeMenu.addChildMenu, sceneNodeMenu::closeAddChildCategoryMenus);
        this.sceneNodeClipboard.setQueueFree(sceneNodeOps::queueFree);
        sceneTreeController.rebuildTree(runtime.state(), "");
        this.sceneNodeOps.setTreeView(sceneTreeController.treeView);
        this.sceneNodeMenu.setTreeView(sceneTreeController.treeView);
    }

    public void handleKey(UiContext ctx, KeyEvent e) {
        if (ctx == null || e == null) {
            return;
        }
        lastUiContext = ctx;
        sceneNodeOps.setLastUiContext(ctx);
        if (e.isPressOrRepeat() && (e.hasCtrl() || e.hasSuper()) && e.key() == InputConstants.KEY_F) {
            filterField.focus(ctx);
            filterChangedAtMs = System.currentTimeMillis();
            return;
        }
        if (sceneTreeController.renamingNodeId >= 0) {
            sceneTreeController.renameTreeField.handleKey(e, ctx.clipboard());
            if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ENTER) {
                sceneTreeController.commitInlineRename();
            } else if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ESCAPE) {
                sceneTreeController.renamingNodeId = -1;
            }
            return;
        }
        if (sceneBranchExporter.saveBranchOpen) {
            if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ESCAPE) {
                sceneBranchExporter.saveBranchOpen = false;
                sceneBranchExporter.saveBranchError = null;
                return;
            }
            if (sceneBranchExporter.saveBranchSceneIdField.isFocused(ctx)) {
                sceneBranchExporter.saveBranchSceneIdField.handleKey(e, ctx.clipboard());
                if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ENTER) {
                    sceneBranchExporter.commitSaveBranch();
                }
                return;
            }
            if (sceneBranchExporter.saveBranchDisplayNameField.isFocused(ctx)) {
                sceneBranchExporter.saveBranchDisplayNameField.handleKey(e, ctx.clipboard());
                if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ENTER) {
                    sceneBranchExporter.commitSaveBranch();
                }
                return;
            }
        }
        if (filterField.isFocused(ctx)) {
            if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ENTER) {
                String filterText = filterField.text();
                if (filterText != null && !filterText.isBlank()) {
                    sceneTreeController.jumpToFirstFilterMatch(filterText.trim().toLowerCase(Locale.ROOT));
                }
                return;
            }
            filterField.handleKey(e, ctx.clipboard());
            filterChangedAtMs = System.currentTimeMillis();
            return;
        }
        if (sceneTreeController.treeView != null && sceneTreeController.treeView.isFocused(ctx) && e.isPressOrRepeat()) {
            if (e.key() == InputConstants.KEY_ESCAPE) {
                EditorState state = runtime.state();
                if (state != null) state.selectedId = 0L;
                sceneTreeController.clearTreeSelection();
                return;
            }
            if (e.key() == InputConstants.KEY_UP || e.key() == InputConstants.KEY_DOWN) {
                sceneTreeController.navigateTree(e.key() == InputConstants.KEY_DOWN);
                return;
            }
            if (e.key() == InputConstants.KEY_LEFT) {
                sceneTreeController.navigateTreeLeft();
                return;
            }
            if (e.key() == InputConstants.KEY_RIGHT) {
                sceneTreeController.navigateTreeRight();
                return;
            }
            if (e.key() == InputConstants.KEY_F2) {
                EditorState state = runtime.state();
                SceneSnapshot.NodeSnapshot selected = state != null ? state.scene.getNode(state.selectedId) : null;
                if (selected != null) {
                    sceneTreeController.beginInlineRename(selected);
                }
                return;
            }
            if (e.key() == InputConstants.KEY_DELETE) {
                EditorState state = runtime.state();
                SceneSnapshot.NodeSnapshot selected = state != null ? state.scene.getNode(state.selectedId) : null;
                if (selected != null && selected.parentId() != 0L) {
                    sceneNodeOps.queueFree(selected.nodeId());
                }
                return;
            }
            boolean ctrl = e.hasCtrl() || e.hasSuper();
            if (ctrl && e.key() == InputConstants.KEY_Z) {
                sceneNodeOps.performUndo();
                return;
            }
            if (ctrl && (e.key() == InputConstants.KEY_Y || (e.hasShift() && e.key() == InputConstants.KEY_Z))) {
                sceneNodeOps.performRedo();
                return;
            }
            if (ctrl && e.key() == InputConstants.KEY_C) {
                sceneNodeClipboard.copySelectedNode();
                return;
            }
            if (ctrl && e.key() == InputConstants.KEY_X) {
                sceneNodeClipboard.cutSelectedNode();
                return;
            }
            if (ctrl && e.key() == InputConstants.KEY_V) {
                if (e.hasShift()) {
                    sceneNodeClipboard.pasteAsSibling();
                } else {
                    sceneNodeClipboard.pasteNodes();
                }
                return;
            }
            if (ctrl && e.hasShift() && e.key() == InputConstants.KEY_D) {
                EditorState state = runtime.state();
                SceneSnapshot.NodeSnapshot selected = state != null ? state.scene.getNode(state.selectedId) : null;
                if (selected != null) {
                    sceneNodeOps.duplicateNodeWithOffset(selected);
                }
                return;
            }
            if (ctrl && !e.hasShift() && e.key() == InputConstants.KEY_D) {
                EditorState state = runtime.state();
                SceneSnapshot.NodeSnapshot selected = state != null ? state.scene.getNode(state.selectedId) : null;
                if (selected != null) {
                    sceneNodeOps.duplicateNode(selected);
                }
                return;
            }
            if (ctrl && e.key() == InputConstants.KEY_A) {
                sceneNodeOps.selectAll();
                return;
            }
            if (ctrl && e.key() == InputConstants.KEY_UP) {
                EditorState s = runtime.state();
                SceneSnapshot.NodeSnapshot sel = s != null ? s.scene.getNode(s.selectedId) : null;
                if (sel != null && sel.parentId() != 0L) sceneNodeOps.moveNode(sel, -1);
                return;
            }
            if (ctrl && e.key() == InputConstants.KEY_DOWN) {
                EditorState s = runtime.state();
                SceneSnapshot.NodeSnapshot sel = s != null ? s.scene.getNode(s.selectedId) : null;
                if (sel != null && sel.parentId() != 0L) sceneNodeOps.moveNode(sel, 1);
                return;
            }
        }
        if (sceneTreeController.treeView != null) {
            sceneTreeController.treeView.handleKey(ctx, e);
        }
    }

    public void handleTextInput(UiContext ctx, TextInputEvent e) {
        if (ctx == null || e == null) {
            return;
        }
        if (sceneBranchExporter.saveBranchOpen) {
            if (sceneBranchExporter.saveBranchSceneIdField.isFocused(ctx)) {
                sceneBranchExporter.saveBranchSceneIdField.handleTextInput(e);
                return;
            }
            if (sceneBranchExporter.saveBranchDisplayNameField.isFocused(ctx)) {
                sceneBranchExporter.saveBranchDisplayNameField.handleTextInput(e);
                return;
            }
        }
        if (filterField.isFocused(ctx)) {
            filterField.handleTextInput(e);
            filterChangedAtMs = System.currentTimeMillis();
            return;
        }
        if (sceneTreeController.renamingNodeId >= 0) {
            sceneTreeController.renameTreeField.handleTextInput(e);
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

        if (sceneBranchExporter.saveBranchOpen) {
            cursorY = sceneBranchExporter.renderSaveBranchRow(ui, r, uiContext, theme, x, cursorY, w, interactive);
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
        boolean needsRebuild = sceneTreeController.treeView == null || sceneTreeController.rootNode == null
                || (filterChanged && filterReady)
                || state.scene.revision() != lastRev
                || !Objects.equals(lastSceneId, currentSceneId);
        if (needsRebuild) {
            sceneNodeMenu.nodeMenu.close();
            sceneNodeMenu.addChildMenu.close();
            sceneNodeMenu.closeAddChildCategoryMenus();
            sceneTreeController.rebuildTree(state, filter);
            sceneNodeMenu.setTreeView(sceneTreeController.treeView);
            lastFilter = filter;
            lastRev = state.scene.revision();
            lastSceneId = currentSceneId;
        }

        // Player start warning banner
        if (state.scene.revision() >= 0 && !SceneNodeOps.sceneIs2D(state) && !SceneNodeOps.sceneHasPlayerStart(state)) {
            int warnH = 22;
            int warnPad = theme.design.space_sm;
            r.drawRect(treeX, treeY, treeW, warnH, EditorTheme.WARNING_BG);
            r.drawText("No PlayerStart in scene", treeX + warnPad, r.baselineForBox(treeY, warnH), EditorTheme.WARNING_TEXT);
            treeY += warnH;
            treeH = Math.max(0, treeH - warnH);
        }

        if (sceneTreeController.treeView != null && treeH > 0) {
            int itemH = Math.max(18, theme.tokens.itemHeight);
            int contentHeight = sceneTreeController.treeView.computeContentHeight();

            long treeSelectedId = 0L;
            {
                var sel = sceneTreeController.treeView.selectedNodes();
                if (!sel.isEmpty()) {
                    var tn = sel.iterator().next();
                    if (tn.data() != null) treeSelectedId = tn.data().nodeId();
                }
            }
            if (state.selectedId != treeSelectedId) {
                sceneTreeController.expandAndSelectNode(state.selectedId, state);
                contentHeight = sceneTreeController.treeView.computeContentHeight();
                int revealScrollY = sceneTreeController.computeRevealScrollY(state.selectedId, itemH, treeH);
                if (revealScrollY >= 0) {
                    ui.setScrollY("sceneTreeScroll", revealScrollY);
                }
            }

            Ui.ScrollArea area = ui.beginScrollArea(r, "sceneTreeScroll", treeX, treeY, treeW, treeH, contentHeight);
            int scrollOffset = (int) area.scrollY();

            sceneTreeController.updateTreeStyle(theme);
            sceneTreeController.treeView.render(r, uiContext, sceneNodeMenu.nodeMenu.isOpen() ? null : input, theme, treeX, treeY, treeW, treeH, scrollOffset, true);
            sceneTreeController.updateSelectionFromTree(state);

            if (sceneTreeController.renamingNodeId >= 0) {
                sceneTreeController.renderInlineRename(r, uiContext, theme, input, treeX, treeY, treeW, itemH, scrollOffset);
            }

            float mx = ui.mouse().x;
            float my = ui.mouse().y;

            if (sceneTreeController.renamingNodeId >= 0 && input != null && input.mousePressed()) {
                boolean hit = mx >= sceneTreeController.renameFieldX && my >= sceneTreeController.renameFieldY
                        && mx < sceneTreeController.renameFieldX + sceneTreeController.renameFieldW
                        && my < sceneTreeController.renameFieldY + sceneTreeController.renameFieldH;
                if (!hit) {
                    sceneTreeController.renamingNodeId = -1;
                    renameJustCancelled = true;
                }
            }

            if (sceneTreeController.renamingNodeId < 0 && !sceneNodeMenu.nodeMenu.isOpen() && input != null && input.mousePressed()
                    && mx >= treeX && mx < treeX + treeW && my >= treeY && my < treeY + treeH) {
                int clickRow = (int) ((my - treeY + scrollOffset) / itemH);
                List<TreeView.VisibleNode<SceneSnapshot.NodeSnapshot>> vis = sceneTreeController.treeView.getVisibleNodes();
                if (clickRow < 0 || clickRow >= vis.size()) {
                    state.selectedId = 0L;
                    sceneTreeController.clearTreeSelection();
                }
            }

            boolean skipClick = renameJustCancelled;
            renameJustCancelled = false;

            boolean rightPressed = interactive && runtime.rightPressed();
            if (!skipClick && rightPressed && !sceneNodeMenu.nodeMenu.isOpen()) {
                if (mx >= treeX && mx < treeX + treeW && my >= treeY && my < treeY + treeH) {
                    sceneTreeController.treeView.handleClick(input, (int) mx, (int) my, treeX, treeY, treeW, treeH, scrollOffset);
                    sceneTreeController.updateSelectionFromTree(state);
                    SceneSnapshot.NodeSnapshot selected = state.scene.getNode(state.selectedId);
                    if (selected != null) {
                        sceneNodeMenu.openNodeMenu(selected);
                        EditorUiUtil.openMenuClamped(sceneNodeMenu.nodeMenu, runtime, (int) mx, (int) my);
                    }
                }
            }

            if (uiContext != null && interactive && !sceneNodeMenu.nodeMenu.isOpen() && sceneTreeController.renamingNodeId < 0) {
                final int dropTreeX = treeX;
                final int dropTreeY = treeY;
                final int dropTreeW = treeW;
                final int dropTreeH = treeH;
                final int dropScrollOffset = scrollOffset;
                final int dropItemH = itemH;
                final TreeView<SceneSnapshot.NodeSnapshot> dropTreeView = sceneTreeController.treeView;
                uiContext.dragDrop().registerDropTarget(new DropTarget<String>(
                        0x51A533,
                        EditorDnD.TYPE_ASSET_PATH,
                        dropTreeX,
                        dropTreeY,
                        dropTreeW,
                        dropTreeH,
                        (String dragPath, float dropX, float dropY) -> {
                            if (dragPath == null
                                    || !(dragPath.endsWith(".js")
                                    || dragPath.endsWith(".mjs")
                                    || dragPath.endsWith(".cjs")
                                    || dragPath.endsWith(".luau"))) {
                                return;
                            }
                            int dropRow = (int) ((dropY - dropTreeY + dropScrollOffset) / dropItemH);
                            List<TreeView.VisibleNode<SceneSnapshot.NodeSnapshot>> vis = dropTreeView.getVisibleNodes();
                            if (dropRow < 0 || dropRow >= vis.size()) {
                                return;
                            }
                            SceneSnapshot.NodeSnapshot dropTarget = vis.get(dropRow).node().data();
                            if (dropTarget == null) {
                                return;
                            }
                            sceneNodeOps.sendOpsRecorded(List.of(new SceneOp.SetProperty(dropTarget.nodeId(), "script", dragPath)));
                            runtime.requestToast("Attached: " + dragPath, false, 1500);
                        }
                ));
            }

            ui.endScrollArea(area);

            if (sceneNodeMenu.nodeMenu.isOpen()) {
                final int menuItemH = Math.max(24, itemH + 2);
                if (input != null) {
                    sceneNodeMenu.nodeMenu.updateFromInput(input, theme, menuItemH);
                    EditorUiUtil.clampOpenMenuToScreen(sceneNodeMenu.nodeMenu, runtime);
                }
                if (interactive) {
                    sceneNodeMenu.syncSubmenus(ui, theme, menuItemH);
                }
                if (input != null && input.mousePressed()) {
                    int cmx = (int) ui.mouse().x;
                    int cmy = (int) ui.mouse().y;
                    boolean insideAny = SceneNodeMenu.inside(sceneNodeMenu.nodeMenu, cmx, cmy, menuItemH)
                            || (sceneNodeMenu.addChildMenu.isOpen() && SceneNodeMenu.inside(sceneNodeMenu.addChildMenu, cmx, cmy, menuItemH));
                    if (!insideAny) {
                        for (ContextMenu menu : sceneNodeMenu.addChildCategoryMenus) {
                            if (menu != null && menu.isOpen() && SceneNodeMenu.inside(menu, cmx, cmy, menuItemH)) {
                                insideAny = true;
                                break;
                            }
                        }
                    }
                    if (!insideAny) {
                        sceneNodeMenu.nodeMenu.close();
                        sceneNodeMenu.addChildMenu.close();
                        sceneNodeMenu.closeAddChildCategoryMenus();
                    } else {
                        if (!sceneNodeMenu.handleSubmenuClick(ui, menuItemH)) {
                            sceneNodeMenu.nodeMenu.handleClick((int) ui.mouse().x, (int) ui.mouse().y, menuItemH);
                            sceneNodeMenu.closeAddChildCategoryMenus();
                            sceneNodeMenu.addChildMenu.close();
                        }
                    }
                }
                final UiRenderer deferR = r;
                final Theme deferTheme = theme;
                final int deferMenuH = menuItemH;
                final boolean deferInteractive = interactive;
                runtime.setOverlayMenuRender(() -> {
                    if (sceneNodeMenu.nodeMenu.isOpen()) {
                        int menuBg = Theme.darkenArgb(Theme.toArgb(deferTheme.panelBg), 0.06f);
                        int menuHover = Theme.mulAlpha(Theme.toArgb(deferTheme.accent), 0.22f);
                        sceneNodeMenu.nodeMenu.render(deferR, deferTheme, deferMenuH,
                                menuBg,
                                menuHover,
                                Theme.toArgb(deferTheme.text),
                                sceneNodeMenu.nodeMenu.hoverIndex());
                    }
                    if (deferInteractive) {
                        sceneNodeMenu.renderSubmenus(deferR, deferTheme, deferMenuH);
                    }
                });
            }
        }

        ui.endPanel();
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

        EditorUiUtil.iconButton(ui, r, theme, expandX, btnY, btnSize, btnSize, Icon.CHEVRON_DOWN, interactive, sceneTreeController::expandAll);
        EditorUiUtil.iconButton(ui, r, theme, collapseX, btnY, btnSize, btnSize, Icon.CHEVRON_RIGHT, interactive, sceneTreeController::collapseAll);
        EditorUiUtil.iconButton(ui, r, theme, addX, btnY, btnSize, btnSize, Icon.ADD, interactive, () -> {
            if (runtime.getCreateNodeDialog() == null) {
                return;
            }
            EditorState state = runtime.state();
            long parentId = state != null && state.selectedId > 0L ? state.selectedId : SceneNodeOps.rootNodeId(state);
            if (parentId <= 0L) return;
            runtime.getCreateNodeDialog().open(parentId);
        });
    }

    public void performUndo() {
        sceneNodeOps.performUndo();
    }

    public void performRedo() {
        sceneNodeOps.performRedo();
    }
}
