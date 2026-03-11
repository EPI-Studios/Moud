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
import com.moud.client.fabric.editor.util.EditorUiUtil;
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
import java.nio.charset.StandardCharsets;

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

    public ScenePanel(EditorRuntime runtime) {
        super("");
        this.runtime = runtime;
        rebuildTree(runtime.state(), "");
    }

    public void handleKey(UiContext ctx, KeyEvent e) {
        if (ctx == null || e == null) {
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
            filterField.handleKey(e, ctx.clipboard());
            return;
        }
        if (treeView != null && treeView.isFocused(ctx) && e.isPressOrRepeat()) {
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

        int tabH = 26;
        int toolbarH = 30;
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
        boolean needsRebuild = treeView == null || rootNode == null
                || !filter.equals(lastFilter)
                || state.scene.revision() != lastRev
                || !Objects.equals(lastSceneId, currentSceneId);
        if (needsRebuild) {
            rebuildTree(state, filter);
            lastFilter = filter;
            lastRev = state.scene.revision();
            lastSceneId = currentSceneId;
        }

        if (treeView != null && treeH > 0) {
            int itemH = Math.max(18, theme.tokens.itemHeight);
            int contentHeight = treeView.computeContentHeight();
            Ui.ScrollArea area = ui.beginScrollArea(r, "sceneTreeScroll", treeX, treeY, treeW, treeH, contentHeight);
            int scrollOffset = (int) area.scrollY();

            updateTreeStyle(theme);
            treeView.render(r, uiContext, input, theme, treeX, treeY, treeW, treeH, scrollOffset, true);
            updateSelectionFromTree(state);

            renderRowToggles(ui, r, theme, input, state, treeX, treeY, treeW, treeH, itemH, scrollOffset, interactive);

            if (renamingNodeId >= 0) {
                renderInlineRename(r, uiContext, theme, input, treeX, treeY, treeW, itemH, scrollOffset);
            }

            float mx = ui.mouse().x;
            float my = ui.mouse().y;

            if (renamingNodeId >= 0 && input != null && input.mousePressed()) {
                boolean hit = mx >= renameFieldX && my >= renameFieldY && mx < renameFieldX + renameFieldW && my < renameFieldY + renameFieldH;
                if (!hit) {
                    renamingNodeId = -1;
                }
            }

            boolean rightPressed = interactive && runtime.rightPressed();
            if (rightPressed && !nodeMenu.isOpen()) {
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
                    if (!handleSubmenuClick(ui, itemH)) {
                        nodeMenu.handleClick((int) ui.mouse().x, (int) ui.mouse().y, itemH);
                        closeAddChildCategoryMenus();
                        addChildMenu.close();
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

        renderTextButton(ui, r, theme, saveX, fieldY, btnW, fieldH, "Save", true, this::commitSaveBranch);
        renderTextButton(ui, r, theme, cancelX, fieldY, cancelW, fieldH, "Cancel", true, () -> {
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
        renderIconButton(ui, r, theme, btnX, btnY, 24, 24, Icon.ADD, interactive, () -> {
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
        int rowIndex = findVisibleRowIndex(rootNode, renamingNodeId, new int[]{0});
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

    private int findVisibleRowIndex(TreeNode<SceneSnapshot.NodeSnapshot> node, long targetId, int[] counter) {
        if (node == null) {
            return -1;
        }
        if (node.data() != null && node.data().nodeId() == targetId) {
            return counter[0];
        }
        counter[0]++;
        if (node.expanded()) {
            for (TreeNode<SceneSnapshot.NodeSnapshot> child : node.children()) {
                int result = findVisibleRowIndex(child, targetId, counter);
                if (result >= 0) {
                    return result;
                }
            }
        }
        return -1;
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
        treeView.setStyle(treeStyle);
    }

    private void updateTreeStyle(Theme theme) {
        treeStyle.rowBgEven = Theme.toArgb(theme.panelBg);
        treeStyle.rowBgOdd = Theme.toArgb(theme.panelBg);
        treeStyle.rowBgHover = Theme.toArgb(theme.widgetHover);
        treeStyle.rowBgSelected = 0xFF2E4A72;
        treeStyle.textColor = Theme.toArgb(theme.text);
        treeStyle.mutedColor = Theme.toArgb(theme.textMuted);
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
        node.setIcon(hasScript(snapshot) ? Icon.CODE : Icon.FILE);
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
        if (snapshot == null) {
            return "";
        }
        String name = snapshot.name() == null ? "" : snapshot.name();
        String prefix = nodeTypePrefix(snapshot.type());
        return prefix.isEmpty() ? name : prefix + " " + name;
    }

    private static String nodeTypePrefix(String type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case "Camera3D"           -> "[Cam]";
            case "PlayerStart"        -> "[PS]";
            case "WorldEnvironment"   -> "[Env]";
            case "CSGBlock"           -> "[Block]";
            case "CSGBox"             -> "[Box]";
            case "MeshInstance3D"     -> "[Mesh]";
            case "SceneInstance3D"    -> "[SI]";
            case "OmniLight3D"        -> "[OL]";
            case "DirectionalLight3D" -> "[DL]";
            case "SpotLight3D"        -> "[SL]";
            case "Node3D"             -> "[3D]";
            default -> "";
        };
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

    private void renderRowToggles(Ui ui,
                                  UiRenderer r,
                                  Theme theme,
                                  UiInput input,
                                  EditorState state,
                                  int treeX,
                                  int treeY,
                                  int treeW,
                                  int treeH,
                                  int itemH,
                                  int scrollOffset,
                                  boolean interactive) {
        if (ui == null || r == null || theme == null || input == null || state == null) {
            return;
        }
        if (rootNode == null) {
            return;
        }

        int btnSize = Math.min(16, Math.max(12, itemH - 6));
        int gap = 4;
        int margin = 6;
        int lockX = treeX + treeW - margin - btnSize;
        int visX = lockX - gap - btnSize;

        float mx = ui.mouse().x;
        float my = ui.mouse().y;
        boolean click = interactive && input.mouseReleased();

        renderRowTogglesRecursive(rootNode, new int[]{0}, treeY, treeY + treeH, itemH, scrollOffset, visX, lockX, btnSize, mx, my, click, theme, r);
    }

    private void renderRowTogglesRecursive(TreeNode<SceneSnapshot.NodeSnapshot> node,
                                          int[] counter,
                                          int treeY,
                                          int treeMaxY,
                                          int itemH,
                                          int scrollOffset,
                                          int visX,
                                          int lockX,
                                          int btnSize,
                                          float mx,
                                          float my,
                                          boolean click,
                                          Theme theme,
                                          UiRenderer r) {
        if (node == null || counter == null || counter.length == 0) {
            return;
        }
        int rowIndex = counter[0];
        SceneSnapshot.NodeSnapshot snap = node.data();
        if (snap != null && snap.nodeId() > 0L) {
            int rowY = treeY + rowIndex * itemH - scrollOffset;
            if (rowY + itemH >= treeY && rowY <= treeMaxY) {
                int btnY = rowY + (itemH - btnSize) / 2;
                boolean visible = isVisible(snap);
                boolean locked = isLocked(snap);

                renderToggleButton(r, theme, visX, btnY, btnSize, btnSize, visible ? "V" : "H", mx, my, click,
                        () -> toggleVisible(snap.nodeId(), visible));
                renderToggleButton(r, theme, lockX, btnY, btnSize, btnSize, locked ? "L" : "U", mx, my, click,
                        () -> toggleLocked(snap.nodeId(), locked));
            }
        }

        counter[0]++;
        if (node.expanded()) {
            for (TreeNode<SceneSnapshot.NodeSnapshot> child : node.children()) {
                renderRowTogglesRecursive(child, counter, treeY, treeMaxY, itemH, scrollOffset, visX, lockX, btnSize, mx, my, click, theme, r);
            }
        }
    }

    private void renderToggleButton(UiRenderer r,
                                    Theme theme,
                                    int x,
                                    int y,
                                    int w,
                                    int h,
                                    String label,
                                    float mx,
                                    float my,
                                    boolean click,
                                    Runnable action) {
        if (r == null || theme == null) {
            return;
        }
        boolean hovered = mx >= x && my >= y && mx < x + w && my < y + h;
        int fill = 0;
        if (hovered) {
            fill = Theme.mulAlpha(Theme.toArgb(theme.widgetHover), 0.65f);
        }
        if (fill != 0) {
            r.drawRoundedRect(x, y, w, h, theme.design.radius_sm, fill);
        }
        int col = hovered ? Theme.toArgb(theme.text) : Theme.toArgb(theme.textMuted);
        String text = label == null ? "" : label;
        float textW = r.measureText(text);
        float tx = x + Math.max(0f, (w - textW) / 2f);
        r.drawText(text, tx, r.baselineForBox(y, h), col);
        if (hovered && click && action != null) {
            action.run();
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
        EditorState state = runtime.state();
        var net = runtime.net();
        Session session = runtime.session();
        if (state == null || net == null || session == null || nodeId <= 0L) {
            return;
        }
        if (currentlyVisible) {
            net.sendOps(session, state, List.of(new SceneOp.SetProperty(nodeId, PROP_VISIBLE, "false")));
        } else {
            net.sendOps(session, state, List.of(new SceneOp.RemoveProperty(nodeId, PROP_VISIBLE)));
        }
    }

    private void toggleLocked(long nodeId, boolean currentlyLocked) {
        EditorState state = runtime.state();
        var net = runtime.net();
        Session session = runtime.session();
        if (state == null || net == null || session == null || nodeId <= 0L) {
            return;
        }
        if (currentlyLocked) {
            net.sendOps(session, state, List.of(
                    new SceneOp.RemoveProperty(nodeId, PROP_EDITOR_LOCKED),
                    new SceneOp.RemoveProperty(nodeId, PROP_LOCKED)
            ));
        } else {
            net.sendOps(session, state, List.of(
                    new SceneOp.SetProperty(nodeId, PROP_EDITOR_LOCKED, "true"),
                    new SceneOp.SetProperty(nodeId, PROP_LOCKED, "true")
            ));
        }
    }

    private void openNodeMenu(SceneSnapshot.NodeSnapshot node) {
        nodeMenu.clear();
        buildAddChildMenu(node);
        nodeMenu.addSubmenu("Add Child", addChildMenu);
        nodeMenu.addSeparator();
        nodeMenu.addItem("Rename", () -> beginInlineRename(node));
        nodeMenu.addItem("Duplicate", () -> duplicateNode(node));
        nodeMenu.addSeparator();
        boolean visible = isVisible(node);
        boolean locked = isLocked(node);
        nodeMenu.addItem(visible ? "Hide" : "Show", () -> toggleVisible(node.nodeId(), visible));
        nodeMenu.addItem(locked ? "Unlock" : "Lock", () -> toggleLocked(node.nodeId(), locked));
        if (node.parentId() != 0L) {
            nodeMenu.addSeparator();
            nodeMenu.addItem("Move Up", () -> moveNode(node, -1));
            nodeMenu.addItem("Move Down", () -> moveNode(node, 1));
            nodeMenu.addSeparator();
            nodeMenu.addItem("Queue free", () -> queueFree(node.nodeId()));
        }
        nodeMenu.addSeparator();
        nodeMenu.addItem("Save Branch as Scene…", () -> openSaveBranch(node));
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
        runtime.net().sendOps(runtime.session(), state, List.of(new SceneOp.Rename(nodeId, next)));
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
        String name = typeId + "_" + (int) (System.nanoTime() % 10_000);
        runtime.net().sendOps(session, state, List.of(new SceneOp.CreateNode(parentId, name, typeId)));
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

    private void duplicateNode(SceneSnapshot.NodeSnapshot node) {
        if (node == null) {
            return;
        }
        nodeMenu.close();
        EditorState state = runtime.state();
        if (state == null) {
            return;
        }
        String name = (node.name() == null ? "node" : node.name()) + "_copy";
        String type = node.type() == null ? "Node" : node.type();
        runtime.net().sendOps(runtime.session(), state,
                List.of(new SceneOp.CreateNode(node.parentId(), name, type)));
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
        runtime.net().sendOps(runtime.session(), state,
                List.of(new SceneOp.Reparent(node.nodeId(), node.parentId(), newIndex)));
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

    private static boolean isRuntimePlayerNode(SceneSnapshot.NodeSnapshot node) {
        if (node == null) {
            return false;
        }
        return "PlayerStart".equals(node.type());
    }
}
