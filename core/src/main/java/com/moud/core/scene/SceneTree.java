package com.moud.core.scene;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class SceneTree {
    private final Node root;
    private final List<DeferredOp> deferred = new ArrayList<>();
    private final AtomicLong nextNodeId = new AtomicLong(1);
    private final Map<Long, Node> nodesById = new HashMap<>();
    private boolean processing;

    public SceneTree(Node root) {
        this.root = Objects.requireNonNull(root);
        this.root.enterTree(this);
    }

    private static boolean wouldCreateCycle(Node node, Node newParent) {
        Node current = newParent;
        while (current != null) {
            if (current == node) {
                return true;
            }
            current = current.parent();
        }
        return false;
    }

    private static boolean reparentImmediate(Node node, Node newParent, int index) {
        Node oldParent = node.parent();
        if (oldParent == null) {
            return false;
        }
        oldParent.childrenInternal().remove(node);

        node.setParentInternal(newParent);
        List<Node> target = newParent.childrenInternal();
        int clamped = Math.max(0, Math.min(index, target.size()));
        target.add(clamped, node);
        return true;
    }

    public Node root() {
        return root;
    }

    public Node getNode(long nodeId) {
        return nodesById.get(nodeId);
    }

    public void tick(double dtSeconds) {
        processing = true;
        try {
            root.processTree(dtSeconds);
        } finally {
            processing = false;
        }
        flushDeferred();
    }

    public boolean processing() {
        return processing;
    }

    public Node getNode(String path) {
        NodePath nodePath = NodePath.parse(path);
        Node current = root;
        if (nodePath.absolute() && !nodePath.parts().isEmpty()) {
            String first = nodePath.parts().getFirst();
            if (!root.name().equals(first)) {
                return null;
            }
        }

        int startIndex = nodePath.absolute() ? 1 : 0;
        for (int i = startIndex; i < nodePath.parts().size(); i++) {
            String part = nodePath.parts().get(i);
            if (".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                current = current.parent();
                if (current == null) {
                    return null;
                }
                continue;
            }
            current = current.findChild(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    void deferAdd(Node parent, Node child) {
        deferred.add(new AddChildOp(parent, child));
    }

    void deferRemove(Node parent, Node child) {
        deferred.add(new RemoveChildOp(parent, child));
    }

    public boolean reparent(long nodeId, long newParentId, int index) {
        Node node = getNode(nodeId);
        Node newParent = getNode(newParentId);
        if (node == null || newParent == null) {
            return false;
        }
        if (node.parent() == null) {
            return false;
        }
        if (wouldCreateCycle(node, newParent)) {
            return false;
        }
        if (processing) {
            deferred.add(new ReparentOp(node, newParent, index));
            return true;
        }
        return reparentImmediate(node, newParent, index);
    }

    private void flushDeferred() {
        int passes = 0;
        while (!deferred.isEmpty()) {
            if (++passes > 1_000) {
                throw new IllegalStateException("Deferred ops did not stabilize");
            }
            List<DeferredOp> ops = List.copyOf(deferred);
            deferred.clear();
            for (DeferredOp op : ops) {
                op.apply();
            }
        }
    }

    void registerNode(Node node) {
        if (node.nodeId() == 0L) {
            node.setNodeId(nextNodeId.getAndIncrement());
        }
        nodesById.put(node.nodeId(), node);
    }

    void unregisterNode(Node node) {
        if (node.nodeId() != 0L) {
            nodesById.remove(node.nodeId(), node);
        }
    }

    private sealed interface DeferredOp permits AddChildOp, RemoveChildOp, ReparentOp {
        void apply();
    }

    private record AddChildOp(Node parent, Node child) implements DeferredOp {
        @Override
        public void apply() {
            parent.addChildImmediate(child);
        }
    }

    private record RemoveChildOp(Node parent, Node child) implements DeferredOp {
        @Override
        public void apply() {
            parent.removeChildImmediate(child);
        }
    }

    private record ReparentOp(Node node, Node newParent, int index) implements DeferredOp {
        @Override
        public void apply() {
            reparentImmediate(node, newParent, index);
        }
    }
}
