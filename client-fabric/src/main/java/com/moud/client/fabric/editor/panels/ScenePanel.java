package com.moud.client.fabric.editor.panels;

import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.theme.EditorTheme;

import com.miry.platform.InputConstants;
import com.miry.ui.PanelContext;
import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.event.KeyEvent;
import com.miry.ui.event.TextInputEvent;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.ContextMenu;
import com.miry.ui.widgets.TextField;
import com.miry.ui.widgets.TreeNode;
import com.miry.ui.widgets.TreeView;
import com.moud.core.NodeTypeDef;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SceneOp;
import com.moud.net.session.Session;

import java.util.*;

public final class ScenePanel extends Panel {
    private final EditorRuntime runtime;
    private final ContextMenu nodeMenu = new ContextMenu();
    private TreeView<SceneSnapshot.NodeSnapshot> treeView;
    private TreeNode<SceneSnapshot.NodeSnapshot> rootNode;
    private Map<Long, TreeNode<SceneSnapshot.NodeSnapshot>> nodeMap = new HashMap<>();

    private boolean createOpen;
    private long createParentId;
    private String createParentName = "";
    private final TextField createSearch = new TextField();
    private boolean createJustOpened;

    public ScenePanel(EditorRuntime runtime) {
        super("Scene");
        this.runtime = runtime;
        rebuildTree();
    }

    public void handleKey(UiContext ctx, KeyEvent e) {
        if (ctx == null || e == null) {
            return;
        }

        if (createOpen) {
            if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ESCAPE) {
                closeCreateDialog();
                return;
            }
            if (createSearch.isFocused(ctx)) {
                createSearch.handleKey(e, ctx.clipboard());
                if (e.isPressOrRepeat() && e.key() == InputConstants.KEY_ENTER) {
                    String first = firstMatchTypeId(runtime.state(), createSearch.text());
                    if (first != null) {
                        createChild(createParentId, first);
                        closeCreateDialog();
                    }
                }
            }
            return;
        }

        if (treeView != null) {
            treeView.handleKey(ctx, e);
        }
    }

    public void handleTextInput(UiContext ctx, TextInputEvent e) {
        if (ctx == null || e == null) {
            return;
        }
        if (createOpen && createSearch.isFocused(ctx)) {
            createSearch.handleTextInput(e);
        }
    }

    @Override
    public void render(PanelContext ctx) {
        Ui ui = ctx.ui();
        UiRenderer r = ctx.renderer();
        Theme theme = ui.theme();
        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        int h = ctx.height();

        ui.beginPanel(x, y, w, h);

        EditorState state = runtime.state();
        Session session = runtime.session();

        int pad = theme.design.space_sm;
        int itemH = theme.design.widget_height_sm;
        int btnH = theme.design.widget_height_md;

        int toolbarY = y + pad;
        int btnX = x + pad;

        if (ui.button(r, "Resync")) {
            runtime.net().requestSnapshot(session, state);
        }

        rebuildTreeIfNeeded(state);

        int treeY = toolbarY + btnH + pad;
        int treeH = h - (treeY - y) - pad;
        int treeW = w - pad * 2;
        int treeX = x + pad;

        if (treeView != null && treeH > 0) {
            int contentHeight = treeView.computeContentHeight();
            Ui.ScrollArea area = ui.beginScrollArea(r, "sceneTreeScroll", treeX, treeY, treeW, treeH, contentHeight);
            int scrollOffset = (int) area.scrollY();

            treeView.render(r, ctx.uiContext(), ui.input(), theme, treeX, treeY, treeW, treeH, scrollOffset, true);

            updateSelectionFromTree(state);

            float mx = ui.mouse().x;
            float my = ui.mouse().y;
            boolean rightPressed = runtime.rightPressed();
            if (rightPressed && !nodeMenu.isOpen() && !createOpen) {
                if (mx >= treeX && mx < treeX + treeW && my >= treeY && my < treeY + treeH) {
                    SceneSnapshot.NodeSnapshot clicked = findClickedNode(state, (int) mx, (int) my, treeX, treeY, scrollOffset);
                    if (clicked != null) {
                        openNodeMenu(theme, clicked);
                        nodeMenu.open((int) mx, (int) my);
                    }
                }
            }

            ui.endScrollArea(area);
        }

        if (nodeMenu.isOpen()) {
            nodeMenu.updateFromInput(ui.input(), theme, itemH);
            nodeMenu.render(r, theme, itemH,
                    Theme.toArgb(theme.panelBg),
                    Theme.toArgb(theme.widgetHover),
                    Theme.toArgb(theme.text),
                    nodeMenu.hoverIndex());
        }

        if (createOpen) {
            renderCreateDialog(ui, r, ctx.uiContext(), theme, x, y, w, h);
        }

        ui.endPanel();
    }

    private void rebuildTree() {
        EditorState state = runtime.state();
        if (state == null || state.scene == null) {
            rootNode = new TreeNode<>(null);
            treeView = new TreeView<>(rootNode, 20);
            treeView.setLabelFunction(this::formatNodeLabel);
            return;
        }

        rebuildTreeFromState(state);
    }

    private void rebuildTreeIfNeeded(EditorState state) {
        if (state.scene == null) {
            return;
        }

        List<SceneSnapshot.NodeSnapshot> roots = state.scene.childrenOf(0L);
        if (rootNode == null || rootNode.children().size() != roots.size()) {
            rebuildTreeFromState(state);
        }
    }

    private void rebuildTreeFromState(EditorState state) {
        nodeMap.clear();
        rootNode = new TreeNode<>(null);

        List<SceneSnapshot.NodeSnapshot> roots = new ArrayList<>(state.scene.childrenOf(0L));
        roots.sort(Comparator.comparing(SceneSnapshot.NodeSnapshot::name));

        for (SceneSnapshot.NodeSnapshot root : roots) {
            TreeNode<SceneSnapshot.NodeSnapshot> node = buildTreeNode(state, root);
            rootNode.addChild(node);
        }

        rootNode.setExpanded(true);
        treeView = new TreeView<>(rootNode, 20);
        treeView.setLabelFunction(this::formatNodeLabel);
        treeView.setIndentStepPx(16);
    }

    private TreeNode<SceneSnapshot.NodeSnapshot> buildTreeNode(EditorState state, SceneSnapshot.NodeSnapshot snapshot) {
        TreeNode<SceneSnapshot.NodeSnapshot> node = new TreeNode<>(snapshot);
        nodeMap.put(snapshot.nodeId(), node);

        List<SceneSnapshot.NodeSnapshot> children = new ArrayList<>(state.scene.childrenOf(snapshot.nodeId()));
        children.sort(Comparator.comparing(SceneSnapshot.NodeSnapshot::name));

        for (SceneSnapshot.NodeSnapshot child : children) {
            TreeNode<SceneSnapshot.NodeSnapshot> childNode = buildTreeNode(state, child);
            node.addChild(childNode);
        }

        node.setExpanded(true);
        return node;
    }

    private String formatNodeLabel(SceneSnapshot.NodeSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        String type = snapshot.type();
        if (type != null && !type.isBlank()) {
            return "[" + type + "] " + snapshot.name();
        }
        return snapshot.name();
    }

    private void updateSelectionFromTree(EditorState state) {
        if (treeView == null) {
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

    private SceneSnapshot.NodeSnapshot findClickedNode(EditorState state, int mx, int my, int treeX, int treeY, int scrollOffset) {
        if (treeView == null) {
            return null;
        }

        Set<TreeNode<SceneSnapshot.NodeSnapshot>> selected = treeView.selectedNodes();
        if (!selected.isEmpty()) {
            return selected.iterator().next().data();
        }
        return null;
    }

    private void openNodeMenu(Theme theme, SceneSnapshot.NodeSnapshot node) {
        nodeMenu.clear();
        nodeMenu.addItem("Add child…", () -> openCreateDialog(node));
        if (node.parentId() != 0L) {
            nodeMenu.addItem("Queue free", () -> queueFree(node.nodeId()));
        }
    }

    private void queueFree(long nodeId) {
        EditorState state = runtime.state();
        Session session = runtime.session();
        runtime.net().sendOps(session, state, List.of(new SceneOp.QueueFree(nodeId)));
        if (state.selectedId == nodeId) {
            state.selectedId = 0L;
        }
        rebuildTree();
    }

    private void openCreateDialog(SceneSnapshot.NodeSnapshot parent) {
        createOpen = true;
        createJustOpened = true;
        createParentId = parent.nodeId();
        createParentName = parent.name() == null ? "" : parent.name();
        createSearch.setText("");
        createSearch.setCursorPos(0);
    }

    private void closeCreateDialog() {
        createOpen = false;
        createJustOpened = false;
        createParentId = 0L;
        createParentName = "";
    }

    private void createChild(long parentId, String typeId) {
        EditorState state = runtime.state();
        Session session = runtime.session();
        String name = defaultChildName(state, parentId, typeId);
        runtime.net().sendOps(session, state, List.of(new SceneOp.CreateNode(parentId, name, typeId)));
        rebuildTree();
    }


    private void renderCreateDialog(Ui ui, UiRenderer r, UiContext uiContext, Theme theme, int x, int y, int w, int h) {
        if (createJustOpened && uiContext != null) {
            createJustOpened = false;
            createSearch.focus(uiContext);
        }

        int overlay = 0x66000000;
        r.drawRect(x, y, w, h, overlay);

        int pad = Math.max(10, theme.design.space_md);
        int dialogW = Math.min(560, Math.max(320, w - pad * 2));
        int dialogH = Math.min(520, Math.max(280, h - pad * 2));
        int dx = x + (w - dialogW) / 2;
        int dy = y + (h - dialogH) / 2;

        float mx = ui.mouse().x;
        float my = ui.mouse().y;
        if (ui.input().mousePressed()) {
            boolean inside = mx >= dx && my >= dy && mx < dx + dialogW && my < dy + dialogH;
            if (!inside) {
                closeCreateDialog();
                return;
            }
        }

        int bg = Theme.toArgb(theme.panelBg);
        int outline = Theme.toArgb(theme.widgetOutline);
        r.drawRoundedRect(dx, dy, dialogW, dialogH, theme.design.radius_md, bg, theme.design.border_thin, outline);

        int headerH = 32;
        int headerBg = Theme.toArgb(theme.headerBg);
        r.drawRoundedRect(dx, dy, dialogW, headerH, theme.design.radius_md, headerBg);
        r.drawRect(dx, dy + headerH - 1, dialogW, 1, Theme.toArgb(theme.headerLine));

        String title = "Create New Node";
        r.drawText(title, dx + 12, r.baselineForBox(dy, headerH), Theme.toArgb(theme.text));

        int contentX = dx + 12;
        int contentY = dy + headerH + 12;
        int contentW = dialogW - 24;
        int itemH = theme.design.widget_height_sm;

        r.drawText("Parent: " + (createParentName.isBlank() ? "#" + createParentId : createParentName),
                contentX, r.baselineForBox(contentY, 18), Theme.toArgb(theme.textMuted));
        contentY += 22;

        createSearch.render(r, uiContext, ui.input(), theme, contentX, contentY, contentW, itemH, true);
        contentY += itemH + 10;

        int listH = Math.max(1, (dy + dialogH - 12) - contentY);
        renderTypeList(ui, r, theme, contentX, contentY, contentW, listH);
    }

    private void renderTypeList(Ui ui, UiRenderer r, Theme theme, int x, int y, int w, int h) {
        EditorState state = runtime.state();
        ArrayList<NodeTypeDef> defs = new ArrayList<>(state.typesById.values());
        defs.removeIf(d -> d == null || d.typeId() == null || d.typeId().isBlank() || "Root".equals(d.typeId()));
        defs.sort(Comparator
                .comparing(NodeTypeDef::category)
                .thenComparingInt(NodeTypeDef::order)
                .thenComparing(NodeTypeDef::uiLabel)
                .thenComparing(NodeTypeDef::typeId));

        String query = createSearch.text() == null ? "" : createSearch.text().trim().toLowerCase(Locale.ROOT);
        ArrayList<NodeTypeDef> filtered = new ArrayList<>();
        for (NodeTypeDef def : defs) {
            String id = def.typeId();
            String label = def.uiLabel();
            if (query.isEmpty()
                    || id.toLowerCase(Locale.ROOT).contains(query)
                    || label.toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(def);
            }
        }

        ArrayList<TypeRow> rows = new ArrayList<>(filtered.size() + 8);
        String lastCat = null;
        for (NodeTypeDef def : filtered) {
            String cat = def.category();
            if (cat == null) {
                cat = "";
            }
            if (!cat.isBlank() && !cat.equals(lastCat)) {
                lastCat = cat;
                rows.add(TypeRow.category(cat));
            }
            rows.add(TypeRow.type(def));
        }

        int rowH = 20;
        int contentHeight = Math.max(1, rows.size() * rowH);
        Ui.ScrollArea area = ui.beginScrollArea(r, "createNodeTypes", x, y, w, h, contentHeight);
        float scroll = area.scrollY();

        float mx = ui.mouse().x;
        float my = ui.mouse().y;
        boolean pressed = ui.input().mousePressed();
        int maxY = y + h;

        int hover = Theme.toArgb(theme.widgetHover);
        int text = Theme.toArgb(theme.text);
        int muted = Theme.toArgb(theme.textMuted);

        for (int i = 0; i < rows.size(); i++) {
            TypeRow row = rows.get(i);
            int yy = Math.round(y - scroll + i * rowH);
            if (yy + rowH < y) continue;
            if (yy > maxY) break;

            boolean hovered = mx >= x && mx < x + w && my >= yy && my < yy + rowH;
            if (hovered) {
                r.drawRect(x, yy, w, rowH, hover);
            }

            int ix = x + 8;
            if (row.category != null) {
                r.drawText(row.category, ix, r.baselineForBox(yy, rowH), muted);
                continue;
            }

            NodeTypeDef def = row.def;
            String label = def == null ? "" : def.uiLabel();
            r.drawText(label, ix, r.baselineForBox(yy, rowH), text);
            String typeId = def == null ? "" : def.typeId();
            float idW = r.measureText(typeId);
            if (idW + 16 < w) {
                r.drawText(typeId, x + w - 8 - idW, r.baselineForBox(yy, rowH), muted);
            }

            if (hovered && pressed && def != null) {
                createChild(createParentId, typeId);
                closeCreateDialog();
            }
        }

        ui.endScrollArea(area);
    }

    private static String firstMatchTypeId(EditorState state, String query) {
        if (state == null) return null;
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (String id : state.typeIds) {
            if (id == null || id.isBlank() || "Root".equals(id)) {
                continue;
            }
            NodeTypeDef def = state.typesById.get(id);
            String label = def == null ? id : def.uiLabel();
            if (q.isEmpty()
                    || id.toLowerCase(Locale.ROOT).contains(q)
                    || label.toLowerCase(Locale.ROOT).contains(q)) {
                return id;
            }
        }
        return null;
    }

    private static String defaultChildName(EditorState state, long parentId, String typeId) {
        if (state == null || typeId == null || typeId.isBlank()) {
            return "Node";
        }
        String base = typeId;
        if (state.scene == null) {
            return base;
        }
        var siblings = state.scene.childrenOf(parentId);
        boolean exists = siblings.stream().anyMatch(n -> base.equals(n.name()));
        if (!exists) {
            return base;
        }
        for (int i = 2; i < 10_000; i++) {
            String name = base + i;
            boolean ok = siblings.stream().noneMatch(n -> name.equals(n.name()));
            if (ok) {
                return name;
            }
        }
        return base + "_" + (int) (System.nanoTime() % 10_000);
    }

    private static final class TypeRow {
        final String category;
        final NodeTypeDef def;

        private TypeRow(String category, NodeTypeDef def) {
            this.category = category;
            this.def = def;
        }

        static TypeRow category(String category) {
            return new TypeRow(category == null ? "" : category, null);
        }

        static TypeRow type(NodeTypeDef def) {
            return new TypeRow(null, def);
        }
    }
}