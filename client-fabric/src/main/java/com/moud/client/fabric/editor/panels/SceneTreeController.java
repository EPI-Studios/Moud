package com.moud.client.fabric.editor.panels;

import com.miry.ui.UiContext;
import com.miry.ui.input.UiInput;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.TextField;
import com.miry.ui.widgets.TreeNode;
import com.miry.ui.widgets.TreeView;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.theme.EditorTheme;
import com.moud.client.fabric.render.MoudIcons;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.session.Session;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.Deque;

class SceneTreeController {
    private final EditorRuntime runtime;

    private SceneNodeOps sceneNodeOps;
    private SceneNodeMenu sceneNodeMenu;

    TreeView<SceneSnapshot.NodeSnapshot> treeView;
    TreeNode<SceneSnapshot.NodeSnapshot> rootNode;
    final TreeView.Style treeStyle = new TreeView.Style();

    long renamingNodeId = -1;
    final TextField renameTreeField = new TextField();
    int renameFieldX;
    int renameFieldY;
    int renameFieldW;
    int renameFieldH;

    final Set<Long> expandedNodeIds = new HashSet<>();

    SceneTreeController(EditorRuntime runtime) {
        this.runtime = runtime;
    }

    void setSceneNodeOps(SceneNodeOps sceneNodeOps) {
        this.sceneNodeOps = sceneNodeOps;
    }

    void setSceneNodeMenu(SceneNodeMenu sceneNodeMenu) {
        this.sceneNodeMenu = sceneNodeMenu;
    }

    void renderInlineRename(UiRenderer r, UiContext uiContext, Theme theme, UiInput input,
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

    void saveExpandedState() {
        if (rootNode == null) return;
        expandedNodeIds.clear();
        collectExpandedIds(rootNode);
    }

    void collectExpandedIds(TreeNode<SceneSnapshot.NodeSnapshot> node) {
        if (node == null) return;
        SceneSnapshot.NodeSnapshot snap = node.data();
        if (snap != null && node.expanded()) {
            expandedNodeIds.add(snap.nodeId());
        }
        for (TreeNode<SceneSnapshot.NodeSnapshot> child : node.children()) {
            collectExpandedIds(child);
        }
    }

    void rebuildTree(EditorState state, String filter) {
        saveExpandedState();
        rootNode = new TreeNode<>(null);
        if (state == null || state.scene == null) {
            treeView = new TreeView<>(rootNode, 24);
            treeView.setLabelFunction(this::formatNodeLabel);
            sceneNodeOps.setTreeView(treeView);
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
        sceneNodeOps.setTreeView(treeView);

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

            boolean visible = SceneNodeOps.isVisible(snap);
            boolean locked  = SceneNodeOps.isLocked(snap);

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
                    sceneNodeOps.toggleVisible(snap.nodeId(), visible);
                } else if (mouseX2 >= lockX && mouseX2 < lockX + btnSize) {
                    sceneNodeOps.toggleLocked(snap.nodeId(), locked);
                }
            }

            return btnSize * 2 + gap + margin + childCountW;
        });

        treeView.setDragReorderListener((dragged, target, zone) -> {
            if (dragged == null || target == null || dragged.nodeId() == target.nodeId()) return;
            EditorState dragState = runtime.state();
            if (dragState == null || runtime.net() == null) return;

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
            sceneNodeOps.sendOpsRecorded(List.of(new SceneOp.Reparent(dragged.nodeId(), newParentId, newIndex)));
        });
    }

    void updateTreeStyle(Theme theme) {
        treeStyle.rowBgEven = Theme.toArgb(theme.panelBg);
        treeStyle.rowBgOdd = Theme.toArgb(theme.panelBg);
        treeStyle.rowBgHover = Theme.toArgb(theme.widgetHover);
        treeStyle.rowBgSelected = Theme.mulAlpha(Theme.toArgb(theme.widgetActive), 0.22f);
        treeStyle.textColor = Theme.toArgb(theme.text);
        treeStyle.mutedColor = Theme.toArgb(theme.textMuted);
        treeStyle.indentOverride = theme.design.icon_sm;
    }

    TreeNode<SceneSnapshot.NodeSnapshot> buildTreeNode(EditorState state, SceneSnapshot.NodeSnapshot snapshot, String filterLower) {
        return buildTreeNode(state, snapshot, filterLower, new HashSet<>());
    }

    TreeNode<SceneSnapshot.NodeSnapshot> buildTreeNode(EditorState state,
                                                       SceneSnapshot.NodeSnapshot snapshot,
                                                       String filterLower,
                                                       Set<Long> path) {
        if (snapshot == null) return null;
        long nodeId = snapshot.nodeId();
        if (nodeId <= 0L) return null;

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

    static boolean hasScript(SceneSnapshot.NodeSnapshot snapshot) {
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

    String formatNodeLabel(SceneSnapshot.NodeSnapshot snapshot) {
        if (snapshot == null) return "";
        return snapshot.name() == null ? "" : snapshot.name();
    }

    static int nodeTypeBadgeColor(String type) {
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

    void updateSelectionFromTree(EditorState state) {
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

    void selectNodeInTree(long nodeId, List<TreeView.VisibleNode<SceneSnapshot.NodeSnapshot>> visible) {
        for (var vn : visible) {
            SceneSnapshot.NodeSnapshot sn = vn.node().data();
            if (sn != null && sn.nodeId() == nodeId) {
                vn.node().setSelected(true);
                treeView.selectedNodes().add(vn.node());
                return;
            }
        }
    }

    void clearTreeSelection() {
        if (treeView == null) return;
        for (TreeNode<SceneSnapshot.NodeSnapshot> tn : treeView.selectedNodes()) {
            tn.setSelected(false);
        }
        treeView.selectedNodes().clear();
    }

    void navigateTree(boolean down) {
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

    void navigateTreeLeft() {
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

    void navigateTreeRight() {
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

    void jumpToFirstFilterMatch(String filterLower) {
        EditorState state = runtime.state();
        if (state == null || state.scene == null || filterLower == null || filterLower.isBlank()) return;
        for (SceneSnapshot.NodeSnapshot node : state.scene.nodes()) {
            if (node == null) continue;
            String name = node.name() == null ? "" : node.name().toLowerCase(Locale.ROOT);
            String type = node.type() == null ? "" : node.type().toLowerCase(Locale.ROOT);
            if (name.contains(filterLower) || type.contains(filterLower)) {
                state.selectedId = node.nodeId();
                runtime.requestFrameSelected();
                return;
            }
        }
    }

    void expandAndSelectNode(long nodeId, EditorState state) {
        if (treeView == null || rootNode == null || state == null || state.scene == null) return;

        Set<Long> ancestors = new HashSet<>();
        SceneSnapshot.NodeSnapshot n = state.scene.getNode(nodeId);
        if (n == null) return;
        long pid = n.parentId();
        while (pid != 0L) {
            ancestors.add(pid);
            SceneSnapshot.NodeSnapshot p = state.scene.getNode(pid);
            if (p == null) break;
            pid = p.parentId();
        }

        if (!ancestors.isEmpty()) {
            Deque<TreeNode<SceneSnapshot.NodeSnapshot>> stack = new ArrayDeque<>();
            stack.push(rootNode);
            while (!stack.isEmpty()) {
                TreeNode<SceneSnapshot.NodeSnapshot> cur = stack.pop();
                SceneSnapshot.NodeSnapshot data = cur.data();
                if (data != null && ancestors.contains(data.nodeId())) {
                    cur.setExpanded(true);
                }
                for (TreeNode<SceneSnapshot.NodeSnapshot> child : cur.children()) {
                    stack.push(child);
                }
            }
        }

        clearTreeSelection();
        selectNodeInTree(nodeId, treeView.getVisibleNodes());
    }

    int computeRevealScrollY(long nodeId, int itemH, int viewHeight) {
        if (treeView == null) return -1;
        List<TreeView.VisibleNode<SceneSnapshot.NodeSnapshot>> visible = treeView.getVisibleNodes();
        for (int i = 0; i < visible.size(); i++) {
            SceneSnapshot.NodeSnapshot sn = visible.get(i).node().data();
            if (sn != null && sn.nodeId() == nodeId) {
                return Math.max(0, i * itemH - viewHeight / 2 + itemH / 2);
            }
        }
        return -1;
    }

    void expandAll() {
        if (rootNode == null) return;
        setExpandedRecursive(rootNode, true);
    }

    void collapseAll() {
        if (rootNode == null) return;
        setExpandedRecursive(rootNode, false);
        rootNode.setExpanded(true);
    }

    static void setExpandedRecursive(TreeNode<?> node, boolean expanded) {
        node.setExpanded(expanded);
        for (TreeNode<?> child : node.children()) {
            setExpandedRecursive(child, expanded);
        }
    }

    void beginInlineRename(SceneSnapshot.NodeSnapshot node) {
        if (node == null) {
            return;
        }
        sceneNodeMenu.nodeMenu.close();
        renamingNodeId = node.nodeId();
        renameTreeField.setText(node.name() == null ? "" : node.name());
        renameTreeField.setCursorPos(renameTreeField.text().length());
    }

    void commitInlineRename() {
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
        sceneNodeOps.sendOpsRecorded(List.of(new SceneOp.Rename(nodeId, next)));
        runtime.requestToast("Renamed to: " + next, false, 1500);
    }

    static String uniqueSiblingName(EditorState state, long parentId, long selfId, String desired) {
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
}

