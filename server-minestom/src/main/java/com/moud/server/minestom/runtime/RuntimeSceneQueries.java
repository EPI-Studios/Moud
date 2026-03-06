package com.moud.server.minestom.runtime;


import com.moud.core.scene.Node;

final class RuntimeSceneQueries {
    private RuntimeSceneQueries() {
    }

    static boolean isRuntimeSubtree(Node node) {
        Node cur = node;
        while (cur != null) {
            String name = cur.name();
            if (name != null && name.startsWith("player_")) {
                return true;
            }
            if (ParseUtils.parseBool(cur.getProperty("@runtime"))
                    || ParseUtils.parseBool(cur.getProperty("runtime_only"))
                    || ParseUtils.parseBool(cur.getProperty("@transient"))) {
                return true;
            }
            cur = cur.parent();
        }
        return false;
    }

    static boolean isDescendantOf(Node node, Node ancestor) {
        if (node == null || ancestor == null) {
            return false;
        }
        Node cur = node;
        while (cur != null) {
            if (cur == ancestor) {
                return true;
            }
            cur = cur.parent();
        }
        return false;
    }

    static boolean hasScript(Node node) {
        if (node == null) {
            return false;
        }
        String v = node.getProperty("script");
        return v != null && !v.trim().isEmpty();
    }
}

