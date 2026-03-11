package com.moud.server.minestom.runtime;

import com.moud.core.scene.Node;
import com.moud.core.util.ParseUtils;
import com.moud.server.minestom.engine.ServerScene;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class RuntimeBodySystem {
    private final Map<PlayerSceneKey, BodyCache> bodyCacheByPlayerScene = new ConcurrentHashMap<>();

    void onDisconnect(UUID uuid) {
        if (uuid == null) {
            return;
        }
        bodyCacheByPlayerScene.keySet().removeIf(k -> uuid.equals(k.playerId()));
    }

    void onSceneChanged(UUID uuid) {
        if (uuid == null) {
            return;
        }
        bodyCacheByPlayerScene.keySet().removeIf(k -> uuid.equals(k.playerId()));
    }

    Node findControllableBody(ServerScene scene, UUID uuid) {
        if (scene == null) {
            return null;
        }
        long graphRev = scene.engine().graphRevision();
        if (uuid != null) {
            BodyCache cached = bodyCacheByPlayerScene.get(new PlayerSceneKey(uuid, scene.sceneId()));
            if (cached != null && cached.graphRevision == graphRev && cached.bodyNodeId > 0L) {
                Node node = scene.engine().sceneTree().getNode(cached.bodyNodeId);
                if (node != null && "CharacterBody3D".equals(scene.engine().nodeTypes().typeIdFor(node)) && !RuntimeSceneQueries.isRuntimeSubtree(node)) {
                    return node;
                }
            }
        }
        Node root = scene.engine().sceneTree().root();

        Node owned = null;
        Node tagged = null;
        Node first = null;

        ArrayList<Node> stack = new ArrayList<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            Node node = stack.remove(stack.size() - 1);
            if (node == null) {
                continue;
            }
            if (node != root && RuntimeSceneQueries.isRuntimeSubtree(node)) {
                continue;
            }

            if ("CharacterBody3D".equals(scene.engine().nodeTypes().typeIdFor(node))) {
                if (first == null) {
                    first = node;
                }
                if (uuid != null && (uuid.toString().equals(node.getProperty("owner_uuid"))
                        || uuid.toString().equals(node.getProperty("owner"))
                        || uuid.toString().equals(node.getProperty("@owner")))) {
                    owned = node;
                    break;
                }
                if (tagged == null && (ParseUtils.parseBool(node.getProperty("player"))
                        || ParseUtils.parseBool(node.getProperty("possess"))
                        || ParseUtils.parseBool(node.getProperty("controlled")))) {
                    tagged = node;
                }
            }

            List<Node> children = node.children();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.add(children.get(i));
            }
        }

        if (owned != null) {
            if (uuid != null) {
                bodyCacheByPlayerScene.put(new PlayerSceneKey(uuid, scene.sceneId()), new BodyCache(graphRev, owned.nodeId()));
            }
            return owned;
        }
        if (tagged != null) {
            if (uuid != null) {
                bodyCacheByPlayerScene.put(new PlayerSceneKey(uuid, scene.sceneId()), new BodyCache(graphRev, tagged.nodeId()));
            }
            return tagged;
        }
        if (uuid != null && first != null) {
            bodyCacheByPlayerScene.put(new PlayerSceneKey(uuid, scene.sceneId()), new BodyCache(graphRev, first.nodeId()));
        }
        return first;
    }

    private record PlayerSceneKey(UUID playerId, String sceneId) {
    }

    private record BodyCache(long graphRevision, long bodyNodeId) {
    }
}

