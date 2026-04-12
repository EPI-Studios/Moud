package com.moud.client.fabric.editor.panels;

import com.miry.ui.UiContext;
import com.miry.ui.widgets.ContextMenu;
import com.miry.ui.widgets.TreeNode;
import com.miry.ui.widgets.TreeView;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.state.EditorHistory;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.core.util.ParseUtils;
import com.moud.core.NodeTypeDef;
import com.moud.core.assets.ResPath;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.session.Session;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SceneNodeOps {
    private static final String PROP_VISIBLE = "visible";
    private static final String PROP_LOCKED = "@locked";
    private static final String PROP_EDITOR_LOCKED = "editor_locked";

    private final EditorRuntime runtime;

    private ContextMenu nodeMenu;
    private ContextMenu addChildMenu;
    private Runnable closeAddChildCategoryMenus = () -> {};

    private TreeView<SceneSnapshot.NodeSnapshot> treeView;
    private UiContext lastUiContext;

    private boolean suppressHistory;

    SceneNodeOps(EditorRuntime runtime) {
        this.runtime = runtime;
    }

    void setMenus(ContextMenu nodeMenu, ContextMenu addChildMenu, Runnable closeAddChildCategoryMenus) {
        this.nodeMenu = nodeMenu;
        this.addChildMenu = addChildMenu;
        this.closeAddChildCategoryMenus = closeAddChildCategoryMenus != null ? closeAddChildCategoryMenus : () -> {};
    }

    void setTreeView(TreeView<SceneSnapshot.NodeSnapshot> treeView) {
        this.treeView = treeView;
    }

    void setLastUiContext(UiContext lastUiContext) {
        this.lastUiContext = lastUiContext;
    }

    private void closeAddChildCategoryMenus() {
        closeAddChildCategoryMenus.run();
    }

    static boolean isVisible(SceneSnapshot.NodeSnapshot node) {
        return boolProp(node, PROP_VISIBLE, true);
    }

    static boolean isLocked(SceneSnapshot.NodeSnapshot node) {
        return boolProp(node, PROP_EDITOR_LOCKED, false) || boolProp(node, PROP_LOCKED, false);
    }

    static boolean boolProp(SceneSnapshot.NodeSnapshot node, String key, boolean fallback) {
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

    void toggleVisible(long nodeId, boolean currentlyVisible) {
        if (nodeId <= 0L || runtime.state() == null || runtime.session() == null || runtime.net() == null) return;
        if (currentlyVisible) {
            sendOpsRecorded(List.of(new SceneOp.SetProperty(nodeId, PROP_VISIBLE, "false")));
        } else {
            sendOpsRecorded(List.of(new SceneOp.RemoveProperty(nodeId, PROP_VISIBLE)));
        }
    }

    void toggleLocked(long nodeId, boolean currentlyLocked) {
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

    void groupSelectedNodes() {
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

    void createChildNode(long parentId, String typeId) {
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

    static long rootNodeId(EditorState state) {
        if (state == null || state.scene == null) return 0L;
        List<SceneSnapshot.NodeSnapshot> roots = state.scene.childrenOf(0L);
        if (roots == null || roots.isEmpty()) return 0L;
        for (SceneSnapshot.NodeSnapshot n : roots) {
            if (n != null && "Root".equals(n.type())) return n.nodeId();
        }
        SceneSnapshot.NodeSnapshot first = roots.getFirst();
        return first != null ? first.nodeId() : 0L;
    }

    static boolean sceneHasPlayerStart(EditorState state) {
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

    static boolean sceneIs2D(EditorState state) {
        if (state == null || state.scene == null) {
            return false;
        }
        List<SceneSnapshot.NodeSnapshot> roots = state.scene.childrenOf(0L);
        if (roots == null || roots.isEmpty()) {
            return false;
        }
        SceneSnapshot.NodeSnapshot root = null;
        for (SceneSnapshot.NodeSnapshot n : roots) {
            if (n != null && "Root".equals(n.type())) {
                root = n;
                break;
            }
        }
        if (root == null) {
            root = roots.getFirst();
        }
        String mode = state.scene.getPropertyValue(root.nodeId(), "scene_mode");
        return mode != null && mode.equalsIgnoreCase("2d");
    }

    static String uniqueChildName(EditorState state, long parentId, String typeId) {
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

    void duplicateNode(SceneSnapshot.NodeSnapshot node) {
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

    void duplicateSelectedNodes() {
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

    void duplicateNodeWithOffset(SceneSnapshot.NodeSnapshot node) {
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

    void duplicateSubtree(EditorState state, SceneSnapshot.NodeSnapshot node, float offset) {
        if (state == null || node == null) {
            return;
        }
        SceneSnapshot.NodeSnapshot parent = state.scene != null ? state.scene.getNode(node.parentId()) : null;
        String parentType = parent != null ? parent.type() : (node.parentId() == 0L || node.parentId() == rootNodeId(state) ? "Root" : null);
        if (!isTypeCompatible(state, runtime, node.type(), parentType)) {
            runtime.requestToast("Cannot duplicate '" + node.name() + "' here (incompatible type).", true, 3000);
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

    EditorHistory.DuplicateSubtreeEntry.CloneSpec buildCloneSpec(EditorState state,
                                                                 long nodeId,
                                                                 boolean isRoot,
                                                                 float rootOffset) {
        if (state == null || state.scene == null || nodeId <= 0L) {
            return null;
        }
        return buildCloneSpec(state, nodeId, isRoot, rootOffset, new HashSet<>());
    }

    EditorHistory.DuplicateSubtreeEntry.CloneSpec buildCloneSpec(EditorState state,
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

    static List<Long> topLevelSelection(EditorState state, Set<Long> selectedIds) {
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

    void moveNode(SceneSnapshot.NodeSnapshot node, int direction) {
        if (node == null || node.nodeId() == rootNodeId(runtime.state())) {
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

    void queueFree(long nodeId) {
        nodeMenu.close();
        addChildMenu.close();
        closeAddChildCategoryMenus();
        EditorState state = runtime.state();
        Session session = runtime.session();
        EditorNet net = runtime.net();
        if (state == null || session == null || net == null) return;
        SceneSnapshot.NodeSnapshot delNode = state.scene.getNode(nodeId);
        if (delNode == null || delNode.nodeId() == rootNodeId(state)) {
            return;
        }
        runtime.history().clearRedo();
        net.sendOps(session, state, List.of(new SceneOp.QueueFree(nodeId)));
        if (state.selectedId == nodeId) {
            state.selectedId = 0L;
        }
        state.selectedIds.remove(nodeId);
        if (delNode != null) {
            runtime.requestToast("Deleted: " + delNode.name(), false, 1500);
        }
    }

    int selectedDeletableCount(SceneSnapshot.NodeSnapshot fallback) {
        EditorState state = runtime.state();
        if (state == null || state.scene == null) {
            return 0;
        }
        Set<Long> ids = selectedNodeIds(fallback);
        long rootId = rootNodeId(state);
        int count = 0;
        for (long id : topLevelSelection(state, deletableNodeIds(state, ids))) {
            SceneSnapshot.NodeSnapshot snap = state.scene.getNode(id);
            if (snap != null && snap.nodeId() != rootId) {
                count++;
            }
        }
        return count;
    }

    void queueFreeSelectedOrNode(SceneSnapshot.NodeSnapshot fallback) {
        nodeMenu.close();
        addChildMenu.close();
        closeAddChildCategoryMenus();
        EditorState state = runtime.state();
        Session session = runtime.session();
        EditorNet net = runtime.net();
        if (state == null || state.scene == null || session == null || net == null) {
            return;
        }

        ArrayList<SceneOp> ops = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        Set<Long> ids = selectedNodeIds(fallback);
        long rootId = rootNodeId(state);
        for (long id : topLevelSelection(state, deletableNodeIds(state, ids))) {
            SceneSnapshot.NodeSnapshot snap = state.scene.getNode(id);
            if (snap == null || snap.nodeId() == rootId) {
                continue;
            }
            ops.add(new SceneOp.QueueFree(id));
            names.add(snap.name() == null ? Long.toString(id) : snap.name());
        }
        if (ops.isEmpty()) {
            return;
        }

        runtime.history().clearRedo();
        net.sendOps(session, state, ops);
        for (SceneOp op : ops) {
            if (op instanceof SceneOp.QueueFree qf) {
                if (state.selectedId == qf.nodeId()) {
                    state.selectedId = 0L;
                }
                state.selectedIds.remove(qf.nodeId());
            }
        }
        if (treeView != null) {
            treeView.selectedNodes().removeIf(tn -> {
                SceneSnapshot.NodeSnapshot snap = tn != null ? tn.data() : null;
                return snap != null && containsQueuedOp(ops, snap.nodeId());
            });
        }
        runtime.requestToast(ops.size() == 1 ? "Deleted: " + names.getFirst() : "Deleted " + ops.size() + " nodes", false, 1500);
    }

    void queueFreeRootChildren(SceneSnapshot.NodeSnapshot root) {
        nodeMenu.close();
        addChildMenu.close();
        closeAddChildCategoryMenus();
        EditorState state = runtime.state();
        Session session = runtime.session();
        EditorNet net = runtime.net();
        if (state == null || state.scene == null || session == null || net == null || root == null || root.nodeId() != rootNodeId(state)) {
            return;
        }

        ArrayList<SceneOp> ops = new ArrayList<>();
        for (SceneSnapshot.NodeSnapshot child : state.scene.childrenOf(root.nodeId())) {
            if (child != null && child.nodeId() > 0L) {
                ops.add(new SceneOp.QueueFree(child.nodeId()));
            }
        }
        if (ops.isEmpty()) {
            return;
        }

        runtime.history().clearRedo();
        net.sendOps(session, state, ops);
        state.selectedId = 0L;
        state.selectedIds.clear();
        if (treeView != null) {
            treeView.selectedNodes().clear();
        }
        runtime.requestToast("Deleted " + ops.size() + " root children", false, 1500);
    }

    private Set<Long> selectedNodeIds(SceneSnapshot.NodeSnapshot fallback) {
        HashSet<Long> ids = new HashSet<>();
        EditorState state = runtime.state();
        if (state != null && state.selectedIds != null) {
            for (long id : state.selectedIds) {
                if (id > 0L) {
                    ids.add(id);
                }
            }
        }
        if (treeView != null) {
            for (TreeNode<SceneSnapshot.NodeSnapshot> tn : treeView.selectedNodes()) {
                SceneSnapshot.NodeSnapshot snap = tn != null ? tn.data() : null;
                if (snap != null && snap.nodeId() > 0L) {
                    ids.add(snap.nodeId());
                }
            }
        }
        if (fallback != null && fallback.nodeId() > 0L && !ids.contains(fallback.nodeId())) {
            ids.clear();
            ids.add(fallback.nodeId());
            return ids;
        }
        if (ids.isEmpty() && fallback != null && fallback.nodeId() > 0L) {
            ids.add(fallback.nodeId());
        }
        return ids;
    }

    private static Set<Long> deletableNodeIds(EditorState state, Set<Long> ids) {
        if (state == null || state.scene == null || ids == null || ids.isEmpty()) {
            return Set.of();
        }
        long rootId = rootNodeId(state);
        HashSet<Long> out = new HashSet<>();
        for (long id : ids) {
            SceneSnapshot.NodeSnapshot snap = state.scene.getNode(id);
            if (snap != null && snap.nodeId() != rootId) {
                out.add(id);
            }
        }
        return out;
    }

    private static boolean containsQueuedOp(List<SceneOp> ops, long nodeId) {
        for (SceneOp op : ops) {
            if (op instanceof SceneOp.QueueFree qf && qf.nodeId() == nodeId) {
                return true;
            }
        }
        return false;
    }

    /** Sends ops, records undo/redo history, and returns the batch ID. */
    long sendOpsRecorded(List<SceneOp> ops) {
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

    void selectAll() {
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

    void copyNodePath(SceneSnapshot.NodeSnapshot node) {
        if (node == null) return;
        nodeMenu.close();
        String path = buildNodePath(node);
        if (lastUiContext != null) {
            lastUiContext.clipboard().setText(path);
        }
        runtime.requestToast("Copied path: " + path, false, 1500);
    }

    String buildNodePath(SceneSnapshot.NodeSnapshot node) {
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

    void openCreateDialog(long parentNodeId) {
        if (runtime.getCreateNodeDialog() == null) {
            return;
        }
        nodeMenu.close();
        closeAddChildCategoryMenus();
        addChildMenu.close();
        runtime.getCreateNodeDialog().open(parentNodeId);
    }

    void moveToRoot(SceneSnapshot.NodeSnapshot node) {
        if (node == null || node.nodeId() == rootNodeId(runtime.state())) return;
        nodeMenu.close();
        EditorState state = runtime.state();
        if (state == null) return;
        int index = state.scene.childrenOf(0L).size();
        sendOpsRecorded(List.of(new SceneOp.Reparent(node.nodeId(), 0L, index)));
    }

    void attachScriptFromFile(long nodeId) {
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
                    "Attach Script (.ts, .js, .luau)", "", null, "Script (.ts, .js, .luau)", false);
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
            String lower = filename.toLowerCase(Locale.ROOT);
            boolean isLuau = lower.endsWith(".luau");
            boolean isTs = lower.endsWith(".ts") || lower.endsWith(".mts");
            if (!(lower.endsWith(".js") || lower.endsWith(".mjs") || lower.endsWith(".cjs") || isTs || isLuau)) {
                filename = filename + ".ts";
                lower = filename.toLowerCase(Locale.ROOT);
                isLuau = false;
                isTs = true;
            }
            String scriptPath = "res://scripts/" + filename;
            try {
                new ResPath(scriptPath);
            } catch (Exception ignored) {
                scriptPath = "res://scripts/node_" + nodeId + (isLuau ? ".luau" : (isTs ? ".ts" : ".js"));
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

    void openChangeTypeDialog(SceneSnapshot.NodeSnapshot node) {
        if (node == null) return;
        nodeMenu.close();
        EditorState state = runtime.state();
        Session session = runtime.session();
        if (state == null || session == null) return;
        if (runtime.getCreateNodeDialog() == null) return;
        long nodeId = node.nodeId();
        boolean changingRoot = nodeId == rootNodeId(state);
        if (changingRoot) {
            runtime.getCreateNodeDialog().open(node.parentId(), typeId -> isRootReplacementType(state, typeId));
        } else {
            runtime.getCreateNodeDialog().open(node.parentId());
        }
        runtime.getCreateNodeDialog().setOnTypeSelected(typeId -> {
            if (typeId != null && !typeId.isBlank()) {
                ArrayList<SceneOp> ops = new ArrayList<>();
                ops.add(new SceneOp.SetProperty(nodeId, "@type", typeId));
                if (changingRoot) {
                    boolean is2D = is2DType(typeId, state.typesById);
                    ops.add(new SceneOp.SetProperty(nodeId, "scene_mode", is2D ? "2d" : "3d"));
                    runtime.setViewportMode(is2D ? EditorRuntime.ViewportMode.TWO_D : EditorRuntime.ViewportMode.THREE_D);
                }
                sendOpsRecorded(ops);
            }
        });
    }

    static boolean isRuntimePlayerNode(SceneSnapshot.NodeSnapshot node) {
        if (node == null) {
            return false;
        }
        return "PlayerStart".equals(node.type());
    }

    static boolean isRootReplacementType(EditorState state, String typeId) {
        if (typeId == null || typeId.isBlank() || "Root".equals(typeId) || "Ticker".equals(typeId)) {
            return false;
        }
        return "Node3D".equals(typeId)
                || "Node2D".equals(typeId)
                || "Control".equals(typeId)
                || is3DType(typeId, state != null ? state.typesById : null)
                || is2DType(typeId, state != null ? state.typesById : null);
    }

    public static boolean isTypeCompatible(EditorState state, EditorRuntime runtime, String typeId, String parentTypeId) {
        if (state == null || typeId == null || typeId.isBlank()) {
            return false;
        }
        Map<String, NodeTypeDef> types = state.typesById;
        boolean scene2d = sceneIs2D(state);
        boolean typeIs2D = is2DType(typeId, types);
        boolean typeIs3D = is3DType(typeId, types);
        boolean parentIs2D = is2DType(parentTypeId, types);
        boolean parentIsRoot = parentTypeId == null || "Root".equals(parentTypeId);

        if (scene2d && typeIs3D) {
            return false;
        }
        if (typeIs2D && !"CanvasLayer".equals(typeId) && !parentIs2D && !(scene2d && parentIsRoot)) {
            return false;
        }
        return true;
    }

    static boolean is2DType(String typeId, Map<String, NodeTypeDef> typesById) {
        return "CanvasLayer".equals(typeId)
                || "CanvasItem".equals(typeId)
                || "Control".equals(typeId)
                || "Node2D".equals(typeId)
                || isDescendantOf(typeId, "CanvasLayer", typesById)
                || isDescendantOf(typeId, "CanvasItem", typesById)
                || isDescendantOf(typeId, "Control", typesById)
                || isDescendantOf(typeId, "Node2D", typesById)
                || CanvasNodeTypes.CANVAS_2D_TYPES.contains(typeId);
    }

    static boolean is3DType(String typeId, Map<String, NodeTypeDef> typesById) {
        return "Node3D".equals(typeId)
                || "WorldEnvironment".equals(typeId)
                || "PlayerStart".equals(typeId)
                || "PlayerAttachment".equals(typeId)
                || isDescendantOf(typeId, "Node3D", typesById);
    }

    static boolean isDescendantOf(String typeId, String ancestorId, Map<String, NodeTypeDef> typesById) {
        if (typeId == null || ancestorId == null || typesById == null) {
            return false;
        }
        String current = typeId;
        int depth = 0;
        while (current != null && depth < 32) {
            if (current.equals(ancestorId)) {
                return true;
            }
            NodeTypeDef def = typesById.get(current);
            current = def != null ? def.parentTypeId() : null;
            depth++;
        }
        return false;
    }
}
