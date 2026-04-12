package com.moud.client.fabric.editor.panels;

import com.moud.client.fabric.editor.state.EditorHistory;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.session.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

class SceneNodeClipboard {
    private record ClipboardEntry(String name, String type, List<SceneSnapshot.Property> properties) {}

    private final EditorRuntime runtime;

    private final List<ClipboardEntry> clipboard = new ArrayList<>();
    private long cutNodeId = -1;

    private LongConsumer queueFree = ignored -> {};

    SceneNodeClipboard(EditorRuntime runtime) {
        this.runtime = runtime;
    }

    void setQueueFree(LongConsumer queueFree) {
        this.queueFree = queueFree != null ? queueFree : ignored -> {};
    }

    void copySelectedNode() {
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

    void pasteNodes() {
        pasteNodesInternal(false);
    }

    void pasteAsSibling() {
        pasteNodesInternal(true);
    }

    void pasteNodesInternal(boolean asSibling) {
        if (clipboard.isEmpty()) return;
        EditorState state = runtime.state();
        Session session = runtime.session();
        if (state == null || session == null) return;
        long parentId;
        SceneSnapshot.NodeSnapshot sel = state.scene.getNode(state.selectedId);
        long rootId = SceneNodeOps.rootNodeId(state);
        if (asSibling && sel != null && sel.nodeId() != rootId) {
            parentId = sel.parentId();
        } else if (sel != null) {
            parentId = sel.nodeId();
        } else {
            parentId = rootId;
        }
        long finalParentId = parentId;
        long pendingCut = cutNodeId;
        cutNodeId = -1;
        SceneSnapshot.NodeSnapshot parentNode = state.scene.getNode(finalParentId);
        String parentType = parentNode != null ? parentNode.type() : (finalParentId == 0L || finalParentId == rootId ? "Root" : null);
        int pastedCount = 0;
        for (ClipboardEntry entry : clipboard) {
            if (entry == null || !SceneNodeOps.isTypeCompatible(state, runtime, entry.type(), parentType)) {
                String name = entry != null && entry.name() != null ? entry.name() : "node";
                runtime.requestToast("Cannot paste '" + name + "' here (incompatible type).", true, 3000);
                continue;
            }
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
            pastedCount++;
        }
        if (pendingCut > 0L) {
            queueFree(pendingCut);
        }
        if (pastedCount > 0) {
            runtime.requestToast("Pasted " + pastedCount + " node" + (pastedCount == 1 ? "" : "s"), false, 1500);
        }
    }

    void cutSelectedNode() {
        EditorState state = runtime.state();
        if (state == null) return;
        SceneSnapshot.NodeSnapshot selected = state.scene.getNode(state.selectedId);
        if (selected == null || selected.nodeId() == SceneNodeOps.rootNodeId(state)) return;
        copySelectedNode();
        cutNodeId = selected.nodeId();
        runtime.requestToast("Cut: " + selected.name(), false, 1500);
    }

    void queueFree(long nodeId) {
        queueFree.accept(nodeId);
    }
}
