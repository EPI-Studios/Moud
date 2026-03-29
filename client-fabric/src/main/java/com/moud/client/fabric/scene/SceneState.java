package com.moud.client.fabric.scene;

import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SceneState {
    private long revision = -1;
    private final Map<Long, SceneSnapshot.NodeSnapshot> nodesById = new HashMap<>();
    private final Map<Long, List<SceneSnapshot.NodeSnapshot>> childrenByParent = new HashMap<>();

    public void clear() {
        revision = -1;
        nodesById.clear();
        childrenByParent.clear();
    }

    public void applySnapshot(SceneSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        revision = snapshot.revision();
        nodesById.clear();
        childrenByParent.clear();
        for (SceneSnapshot.NodeSnapshot node : snapshot.nodes()) {
            nodesById.put(node.nodeId(), node);
            childrenByParent.computeIfAbsent(node.parentId(), k -> new ArrayList<>()).add(node);
        }
    }

    public long revision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public SceneSnapshot.NodeSnapshot getNode(long nodeId) {
        return nodesById.get(nodeId);
    }

    public Collection<SceneSnapshot.NodeSnapshot> nodes() {
        return nodesById.values();
    }

    public List<SceneSnapshot.NodeSnapshot> childrenOf(long parentId) {
        return childrenByParent.getOrDefault(parentId, List.of());
    }

    public String getPropertyValue(long nodeId, String key) {
        SceneSnapshot.NodeSnapshot node = nodesById.get(nodeId);
        if (node == null || key == null) return null;
        List<SceneSnapshot.Property> props = node.properties();
        if (props == null) return null;
        for (SceneSnapshot.Property p : props) {
            if (p != null && key.equals(p.key())) return p.value();
        }
        return null;
    }

    public int indexOfChild(long parentId, long nodeId) {
        List<SceneSnapshot.NodeSnapshot> children = childrenOf(parentId);
        for (int i = 0; i < children.size(); i++) {
            SceneSnapshot.NodeSnapshot c = children.get(i);
            if (c != null && c.nodeId() == nodeId) return i;
        }
        return -1;
    }

    public void applyOps(List<SceneOp> ops) {
        if (ops == null || ops.isEmpty()) {
            return;
        }
        boolean graphChanged = false;
        for (SceneOp op : ops) {
            if (op instanceof SceneOp.SetProperty sp) {
                applySetProperty(sp.nodeId(), sp.key(), sp.value());
            } else if (op instanceof SceneOp.RemoveProperty rp) {
                applyRemoveProperty(rp.nodeId(), rp.key());
            } else if (op instanceof SceneOp.Rename rn) {
                applyRename(rn.nodeId(), rn.newName());
                graphChanged = true;
            } else if (op instanceof SceneOp.Reparent re) {
                applyReparent(re.nodeId(), re.newParentId(), re.index());
                graphChanged = true;
            } else if (op instanceof SceneOp.QueueFree qf) {
                applyQueueFree(qf.nodeId());
                graphChanged = true;
            }
        }
        if (graphChanged && revision >= 0) {
            revision++;
        }
    }

    private void applySetProperty(long nodeId, String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        SceneSnapshot.NodeSnapshot node = nodesById.get(nodeId);
        if (node == null) {
            return;
        }
        List<SceneSnapshot.Property> props = node.properties() != null ? node.properties() : List.of();
        ArrayList<SceneSnapshot.Property> out = new ArrayList<>(props.size() + 1);
        boolean found = false;
        for (SceneSnapshot.Property p : props) {
            if (p == null || p.key() == null) {
                continue;
            }
            if (p.key().equals(key)) {
                out.add(new SceneSnapshot.Property(key, value));
                found = true;
            } else {
                out.add(p);
            }
        }
        if (!found) {
            out.add(new SceneSnapshot.Property(key, value));
        }
        replaceNode(new SceneSnapshot.NodeSnapshot(node.nodeId(), node.parentId(), node.name(), node.type(), List.copyOf(out), node.uniforms()));
    }

    private void applyRemoveProperty(long nodeId, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        SceneSnapshot.NodeSnapshot node = nodesById.get(nodeId);
        if (node == null) {
            return;
        }
        List<SceneSnapshot.Property> props = node.properties() != null ? node.properties() : List.of();
        ArrayList<SceneSnapshot.Property> out = new ArrayList<>(props.size());
        for (SceneSnapshot.Property p : props) {
            if (p == null || p.key() == null) {
                continue;
            }
            if (!p.key().equals(key)) {
                out.add(p);
            }
        }
        replaceNode(new SceneSnapshot.NodeSnapshot(node.nodeId(), node.parentId(), node.name(), node.type(), List.copyOf(out), node.uniforms()));
    }

    private void applyRename(long nodeId, String newName) {
        SceneSnapshot.NodeSnapshot node = nodesById.get(nodeId);
        if (node == null) {
            return;
        }
        replaceNode(new SceneSnapshot.NodeSnapshot(node.nodeId(), node.parentId(), newName, node.type(), node.properties(), node.uniforms()));
    }

    private void applyReparent(long nodeId, long newParentId, int index) {
        SceneSnapshot.NodeSnapshot node = nodesById.get(nodeId);
        if (node == null) {
            return;
        }
        long oldParentId = node.parentId();
        List<SceneSnapshot.NodeSnapshot> oldList = childrenByParent.get(oldParentId);
        if (oldList instanceof ArrayList<SceneSnapshot.NodeSnapshot> a) {
            removeFromListById(a, nodeId);
        } else if (oldList != null && !oldList.isEmpty()) {
            ArrayList<SceneSnapshot.NodeSnapshot> copy = new ArrayList<>(oldList);
            removeFromListById(copy, nodeId);
            childrenByParent.put(oldParentId, copy);
        }

        SceneSnapshot.NodeSnapshot moved = new SceneSnapshot.NodeSnapshot(node.nodeId(), newParentId, node.name(), node.type(), node.properties(), node.uniforms());
        nodesById.put(nodeId, moved);

        List<SceneSnapshot.NodeSnapshot> newList = childrenByParent.get(newParentId);
        ArrayList<SceneSnapshot.NodeSnapshot> target;
        if (newList instanceof ArrayList<SceneSnapshot.NodeSnapshot> a) {
            target = a;
        } else {
            target = new ArrayList<>(newList != null ? newList : List.of());
            childrenByParent.put(newParentId, target);
        }
        int clamped = Math.max(0, Math.min(target.size(), index));
        target.add(clamped, moved);
    }

    private void applyQueueFree(long nodeId) {
        if (nodeId <= 0L) {
            return;
        }
        SceneSnapshot.NodeSnapshot root = nodesById.get(nodeId);
        if (root == null) {
            return;
        }
        removeFromParentList(root.parentId(), nodeId);

        ArrayDeque<Long> stack = new ArrayDeque<>();
        stack.push(nodeId);
        while (!stack.isEmpty()) {
            long id = stack.pop();
            if (!nodesById.containsKey(id)) {
                continue;
            }
            List<SceneSnapshot.NodeSnapshot> children = childrenByParent.remove(id);
            if (children != null && !children.isEmpty()) {
                for (int i = children.size() - 1; i >= 0; i--) {
                    SceneSnapshot.NodeSnapshot child = children.get(i);
                    if (child != null) {
                        stack.push(child.nodeId());
                    }
                }
            }
            nodesById.remove(id);
        }
    }

    private void replaceNode(SceneSnapshot.NodeSnapshot next) {
        SceneSnapshot.NodeSnapshot prev = nodesById.put(next.nodeId(), next);
        if (prev == null) {
            childrenByParent.computeIfAbsent(next.parentId(), k -> new ArrayList<>()).add(next);
            return;
        }
        if (prev.parentId() != next.parentId()) {
            removeFromParentList(prev.parentId(), prev.nodeId());
            childrenByParent.computeIfAbsent(next.parentId(), k -> new ArrayList<>()).add(next);
            return;
        }
        List<SceneSnapshot.NodeSnapshot> siblings = childrenByParent.get(prev.parentId());
        if (siblings == null || siblings.isEmpty()) {
            childrenByParent.computeIfAbsent(prev.parentId(), k -> new ArrayList<>()).add(next);
            return;
        }
        if (siblings instanceof ArrayList<SceneSnapshot.NodeSnapshot> a) {
            replaceInListById(a, next);
            return;
        }
        ArrayList<SceneSnapshot.NodeSnapshot> copy = new ArrayList<>(siblings);
        replaceInListById(copy, next);
        childrenByParent.put(prev.parentId(), copy);
    }

    private void removeFromParentList(long parentId, long nodeId) {
        List<SceneSnapshot.NodeSnapshot> list = childrenByParent.get(parentId);
        if (list == null || list.isEmpty()) {
            return;
        }
        if (list instanceof ArrayList<SceneSnapshot.NodeSnapshot> a) {
            removeFromListById(a, nodeId);
            if (a.isEmpty()) {
                childrenByParent.remove(parentId);
            }
            return;
        }
        ArrayList<SceneSnapshot.NodeSnapshot> copy = new ArrayList<>(list);
        removeFromListById(copy, nodeId);
        if (copy.isEmpty()) {
            childrenByParent.remove(parentId);
        } else {
            childrenByParent.put(parentId, copy);
        }
    }

    private static void removeFromListById(ArrayList<SceneSnapshot.NodeSnapshot> list, long nodeId) {
        for (int i = 0; i < list.size(); i++) {
            SceneSnapshot.NodeSnapshot n = list.get(i);
            if (n != null && n.nodeId() == nodeId) {
                list.remove(i);
                return;
            }
        }
    }

    private static void replaceInListById(ArrayList<SceneSnapshot.NodeSnapshot> list, SceneSnapshot.NodeSnapshot next) {
        for (int i = 0; i < list.size(); i++) {
            SceneSnapshot.NodeSnapshot n = list.get(i);
            if (n != null && n.nodeId() == next.nodeId()) {
                list.set(i, next);
                return;
            }
        }
        list.add(next);
    }
}
