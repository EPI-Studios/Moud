package com.moud.core.scene;

import com.moud.core.NodeTypeRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SceneTreeMutator {
    private static final long ROOT_PARENT = -1L;

    private SceneTreeMutator() {
    }

    public record NodeSpec(
            long nodeId,
            long parentId,
            String name,
            String typeId,
            Map<String, String> properties
    ) {
        public NodeSpec {
            if (name == null) {
                name = "";
            }
            if (typeId == null) {
                typeId = "Node";
            }
            if (properties == null) {
                properties = Map.of();
            } else {
                properties = Map.copyOf(properties);
            }
        }
    }

    public static void replaceRootChildren(SceneTree tree, List<NodeSpec> specs, NodeTypeRegistry nodeTypes) {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(specs, "specs");
        Objects.requireNonNull(nodeTypes, "nodeTypes");

        Node root = tree.root();
        for (Node child : List.copyOf(root.childrenInternal())) {
            root.removeChildImmediate(child);
        }

        LinkedHashMap<Long, NodeSpec> byId = new LinkedHashMap<>();
        for (NodeSpec spec : specs) {
            if (spec == null) {
                continue;
            }
            if (spec.nodeId() <= 0L) {
                continue;
            }
            if (spec.name() == null || spec.name().isBlank()) {
                continue;
            }
            byId.put(spec.nodeId(), spec);
        }

        HashMap<Long, PlainNode> nodes = new HashMap<>(byId.size());
        for (NodeSpec spec : byId.values()) {
            PlainNode node = new PlainNode(spec.name());
            node.setNodeId(spec.nodeId());

            String typeId = (spec.typeId() == null || spec.typeId().isBlank()) ? "Node" : spec.typeId();
            if (!"Node".equals(typeId) && (node.getProperty("@type") == null || node.getProperty("@type").isBlank())) {
                node.setProperty("@type", typeId);
            }

            for (Map.Entry<String, String> entry : spec.properties().entrySet()) {
                if (entry == null) {
                    continue;
                }
                String k = entry.getKey();
                String v = entry.getValue();
                if (k == null || k.isBlank() || v == null) {
                    continue;
                }
                node.setProperty(k, v);
            }

            nodeTypes.applyDefaults(node, typeId);
            nodes.put(spec.nodeId(), node);
        }

        HashMap<Long, ArrayList<PlainNode>> childrenByParent = new HashMap<>();
        for (NodeSpec spec : byId.values()) {
            PlainNode node = nodes.get(spec.nodeId());
            if (node == null) {
                continue;
            }
            long parentId = spec.parentId();
            if (parentId == 0L || !byId.containsKey(parentId)) {
                parentId = ROOT_PARENT;
            }
            childrenByParent.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(node);
        }

        attachChildren(root, ROOT_PARENT, childrenByParent);
    }

    private static void attachChildren(Node parent,
                                       long parentIdKey,
                                       Map<Long, ArrayList<PlainNode>> childrenByParent) {
        ArrayList<PlainNode> children = childrenByParent.get(parentIdKey);
        if (children == null || children.isEmpty()) {
            return;
        }
        for (PlainNode child : children) {
            if (child.parent() != null) {
                continue;
            }
            parent.addChildImmediate(child);
            attachChildren(child, child.nodeId(), childrenByParent);
        }
    }
}
