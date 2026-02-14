package com.moud.client.fabric.editor.panels;

import com.miry.ui.PanelContext;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.ContextMenu;
import com.miry.ui.widgets.StripTabs;
import com.miry.ui.widgets.TextField;
import com.miry.ui.widgets.TreeNode;
import com.miry.ui.widgets.TreeView;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.session.Session;

import java.util.*;

public final class ScenePanel extends Panel {
    private final EditorRuntime runtime;

    private final StripTabs dockTabs = new StripTabs();
    private final StripTabs.Style dockTabStyle = new StripTabs.Style();

    private final TextField filterField = new TextField();
    private final ContextMenu nodeMenu = new ContextMenu();

    private TreeView<SceneSnapshot.NodeSnapshot> treeView;
    private TreeNode<SceneSnapshot.NodeSnapshot> rootNode;
    private final TreeView.Style treeStyle = new TreeView.Style();

    private long lastRev = Long.MIN_VALUE;

    public ScenePanel(EditorRuntime runtime) {
        super("");
        this.runtime = runtime;
        rebuildTree(runtime.state(), "");
    }

    public void handleKey(UiContext ctx, KeyEvent e) {
        if (ctx == null || e == null) {
            return;
        }
        if (filterField.isFocused(ctx)) {
            filterField.handleKey(e, ctx.clipboard());
            return;
        }
        if (treeView != null) {
            treeView.handleKey(ctx, e);
        }
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
        int pad = theme.design.space_sm;

        int cursorY = y;
        renderDockTabs(ui, r, uiContext, theme, x, cursorY, w, tabH);
        cursorY += tabH;

        renderToolbar(ui, r, uiContext, theme, x, cursorY, w, toolbarH);
        cursorY += toolbarH;

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

        String filter = filterField.text() == null ? "" : filterField.text().trim();
        if (state.scene.revision() != lastRev) {
            lastRev = state.scene.revision();
            rebuildTree(state, filter);
        } else if (treeView == null || rootNode == null) {
            rebuildTree(state, filter);
        }

        if (treeView != null && treeH > 0) {
            int itemH = Math.max(18, theme.tokens.itemHeight);
            int contentHeight = treeView.computeContentHeight();
            Ui.ScrollArea area = ui.beginScrollArea(r, "sceneTreeScroll", treeX, treeY, treeW, treeH, contentHeight);
            int scrollOffset = (int) area.scrollY();

            treeView.render(r, uiContext, ui.input(), theme, treeX, treeY, treeW, treeH, scrollOffset, true);
            updateSelectionFromTree(state);

            float mx = ui.mouse().x;
            float my = ui.mouse().y;
            boolean rightPressed = runtime.rightPressed();
            if (rightPressed && !nodeMenu.isOpen()) {
                if (mx >= treeX && mx < treeX + treeW && my >= treeY && my < treeY + treeH) {
                    treeView.handleClick(ui.input(), (int) mx, (int) my, treeX, treeY, treeW, treeH, scrollOffset);
                    updateSelectionFromTree(state);
                    SceneSnapshot.NodeSnapshot selected = state.scene.getNode(state.selectedId);
                    if (selected != null) {
                        openNodeMenu(selected);
                        nodeMenu.open((int) mx, (int) my);
                    }
                }
            }

            ui.endScrollArea(area);

            if (nodeMenu.isOpen()) {
                nodeMenu.updateFromInput(ui.input(), theme, itemH);
                nodeMenu.render(r, theme, itemH,
                        Theme.toArgb(theme.panelBg),
                        Theme.toArgb(theme.widgetHover),
                        Theme.toArgb(theme.text),
                        nodeMenu.hoverIndex());
                if (ui.input().mousePressed()) {
                    nodeMenu.handleClick((int) ui.mouse().x, (int) ui.mouse().y, itemH);
                }
            }
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

        String[] labels = new String[]{"Scene"};
        dockTabs.render(r, uiContext, input, theme, x, y, w, h, labels, 0, true, dockTabStyle);
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
            r.drawText("Filter Nodes", searchX + 6 + iconSize + 6, r.baselineForBox(searchY, searchH), hint);
        }

        int btnX = x + w - pad - 24;
        int btnY = y + (h - 24) / 2;
        renderIconButton(ui, r, theme, btnX, btnY, 24, 24, Icon.ADD, true, () -> {
            if (runtime.getCreateNodeDialog() == null) {
                return;
            }
            EditorState state = runtime.state();
            long parentId = state != null ? state.selectedId : 0L;
            runtime.getCreateNodeDialog().open(parentId);
        });
    }

    private void rebuildTree(EditorState state, String filter) {
        rootNode = new TreeNode<>(null);
        if (state == null || state.scene == null) {
            treeView = new TreeView<>(rootNode, 20);
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
        treeView = new TreeView<>(rootNode, 20);
        treeView.setLabelFunction(this::formatNodeLabel);
        treeView.setIndentStepPx(16);

        treeStyle.drawContainer = false;
        treeStyle.drawFocusRing = false;
        treeStyle.stripedRows = false;
        treeStyle.rowBgEven = 0xFF212529;
        treeStyle.rowBgOdd = 0xFF212529;
        treeStyle.rowBgHover = 0xFF404553;
        treeStyle.rowBgSelected = 0xFF3D5E89;
        treeStyle.textColor = 0xFFE0E0E0;
        treeStyle.mutedColor = 0xFFB3B3B3;
        treeView.setStyle(treeStyle);
    }

    private TreeNode<SceneSnapshot.NodeSnapshot> buildTreeNode(EditorState state, SceneSnapshot.NodeSnapshot snapshot, String filterLower) {
        if (snapshot == null) {
            return null;
        }

        List<TreeNode<SceneSnapshot.NodeSnapshot>> childNodes = new ArrayList<>();
        List<SceneSnapshot.NodeSnapshot> children = new ArrayList<>(state.scene.childrenOf(snapshot.nodeId()));
        children.sort(Comparator.comparing(SceneSnapshot.NodeSnapshot::name));
        for (SceneSnapshot.NodeSnapshot child : children) {
            TreeNode<SceneSnapshot.NodeSnapshot> cn = buildTreeNode(state, child, filterLower);
            if (cn != null) {
                childNodes.add(cn);
            }
        }

        boolean matches = filterLower == null || filterLower.isEmpty()
                || (snapshot.name() != null && snapshot.name().toLowerCase(Locale.ROOT).contains(filterLower))
                || (snapshot.type() != null && snapshot.type().toLowerCase(Locale.ROOT).contains(filterLower));
        if (!matches && childNodes.isEmpty()) {
            return null;
        }

        TreeNode<SceneSnapshot.NodeSnapshot> node = new TreeNode<>(snapshot);
        for (TreeNode<SceneSnapshot.NodeSnapshot> cn : childNodes) {
            node.addChild(cn);
        }
        node.setExpanded(true);
        node.setIcon(Icon.FILE);
        return node;
    }

    private String formatNodeLabel(SceneSnapshot.NodeSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        return snapshot.name();
    }

    private void updateSelectionFromTree(EditorState state) {
        if (treeView == null || state == null) {
            return;
        }

        Set<TreeNode<SceneSnapshot.NodeSnapshot>> selected = treeView.selectedNodes();
        if (!selected.isEmpty()) {
            TreeNode<SceneSnapshot.NodeSnapshot> node = selected.iterator().next();
            if (node.data() != null) {
                state.selectedId = node.data().nodeId();
            }
        }
    }

    private void openNodeMenu(SceneSnapshot.NodeSnapshot node) {
        nodeMenu.clear();
        nodeMenu.addItem("Add child…", () -> openCreateDialog(node.nodeId()));
        if (node.parentId() != 0L) {
            nodeMenu.addItem("Queue free", () -> queueFree(node.nodeId()));
        }
    }

    private void queueFree(long nodeId) {
        EditorState state = runtime.state();
        Session session = runtime.session();
        runtime.net().sendOps(session, state, List.of(new SceneOp.QueueFree(nodeId)));
        if (state != null && state.selectedId == nodeId) {
            state.selectedId = 0L;
        }
        if (state != null) {
            rebuildTree(state, filterField.text());
        }
    }

    private void openCreateDialog(long parentNodeId) {
        if (runtime.getCreateNodeDialog() == null) {
            return;
        }
        nodeMenu.close();
        runtime.getCreateNodeDialog().open(parentNodeId);
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
