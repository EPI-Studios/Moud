package com.moud.core.scene;

import java.util.*;


public abstract class Node {
    private final List<Node> children = new ArrayList<>();
    private final Map<String, String> properties = new LinkedHashMap<>();
    private long nodeId;
    private String name;
    private Node parent;
    private SceneTree tree;
    private boolean readyCalled;

    protected Node(String name) {
        this.name = Objects.requireNonNull(name);
    }

    private static void buildPath(StringBuilder sb, Node node) {
        if (node.parent != null) {
            buildPath(sb, node.parent);
        }
        sb.append('/');
        sb.append(node.name);
    }

    public final long nodeId() {
        return nodeId;
    }

    final void setNodeId(long nodeId) {
        this.nodeId = nodeId;
    }

    public final String name() {
        return name;
    }

    public final void setName(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public final Node parent() {
        return parent;
    }

    public final SceneTree tree() {
        return tree;
    }

    public final List<Node> children() {
        return Collections.unmodifiableList(children);
    }

    final List<Node> childrenInternal() {
        return children;
    }

    final void setParentInternal(Node parent) {
        this.parent = parent;
    }

    public final Map<String, String> properties() {
        return Collections.unmodifiableMap(properties);
    }

    public final String getProperty(String key) {
        Objects.requireNonNull(key);
        return properties.get(key);
    }

    public final void setProperty(String key, String value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        properties.put(key, value);
    }

    public final void removeProperty(String key) {
        Objects.requireNonNull(key);
        properties.remove(key);
    }

    public final String path() {
        StringBuilder sb = new StringBuilder();
        buildPath(sb, this);
        return sb.toString();
    }

    public final Node findChild(String childName) {
        Objects.requireNonNull(childName);
        for (Node child : children) {
            if (childName.equals(child.name)) {
                return child;
            }
        }
        return null;
    }

    public final Node getNode(String path) {
        Objects.requireNonNull(path);
        if (tree == null) {
            return null;
        }
        NodePath nodePath = NodePath.parse(path);
        if (nodePath.absolute()) {
            return tree.getNode(path);
        }
        return resolveRelative(nodePath);
    }

    private Node resolveRelative(NodePath nodePath) {
        Node current = this;
        for (String part : nodePath.parts()) {
            if (".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                current = current.parent;
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

    public final void addChild(Node child) {
        if (tree != null) {
            if (tree.processing()) {
                tree.deferAdd(this, child);
                return;
            }
        }
        addChildImmediate(child);
    }

    final void addChildImmediate(Node child) {
        Objects.requireNonNull(child);
        if (child.parent != null) {
            throw new IllegalStateException("Child already has a parent: " + child.name);
        }
        if (child.tree != null) {
            throw new IllegalStateException("Child already in a tree: " + child.name);
        }
        child.parent = this;
        children.add(child);
        if (tree != null) {
            child.enterTree(tree);
        }
    }

    public final void removeChild(Node child) {
        if (tree != null) {
            if (tree.processing()) {
                tree.deferRemove(this, child);
                return;
            }
        }
        removeChildImmediate(child);
    }

    final void removeChildImmediate(Node child) {
        Objects.requireNonNull(child);
        if (child.parent != this) {
            return;
        }
        if (children.remove(child)) {
            if (child.tree != null) {
                child.exitTree();
            }
            child.parent = null;
        }
    }

    public final void queueFree() {
        if (parent == null) {
            return;
        }
        parent.removeChild(this);
    }

    final void enterTree(SceneTree tree) {
        this.tree = Objects.requireNonNull(tree);
        tree.registerNode(this);
        onEnterTree();
        for (Node child : children) {
            child.enterTree(tree);
        }
    }

    final void exitTree() {
        for (Node child : children) {
            if (child.tree != null) {
                child.exitTree();
            }
        }
        onExitTree();
        SceneTree oldTree = tree;
        tree = null;
        if (oldTree != null) {
            oldTree.unregisterNode(this);
        }
        readyCalled = false;
    }

    final void processTree(double dtSeconds) {
        if (!readyCalled) {
            readyCalled = true;
            onReady();
        }
        onProcess(dtSeconds);
        for (Node child : List.copyOf(children)) {
            child.processTree(dtSeconds);
        }
    }

    protected void onEnterTree() {
    }

    protected void onReady() {
    }

    protected void onProcess(double dtSeconds) {
    }

    protected void onExitTree() {
    }
}
