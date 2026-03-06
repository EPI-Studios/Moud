package com.moud.server.minestom.engine;

import com.moud.core.NodeTypeRegistry;
import com.moud.core.scene.Node;
import com.moud.core.scene.PlainNode;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneOpResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class SceneInstancer {
    public static final String TYPE_SCENE_INSTANCE = "SceneInstance3D";
    public static final String PROP_SCENE_ID = "scene_id";

    private static final String PROP_RUNTIME = "@runtime";
    private static final String PROP_TRANSIENT = "@transient";
    private static final String PROP_RUNTIME_ONLY = "runtime_only";
    private static final String LEGACY_PLAYER_PREFIX = "player_";
    private static final HashSet<String> EXCLUDED_TYPE_IDS = new HashSet<>(java.util.Set.of("Root", "Ticker"));

    private static final String PROP_LOCKED = "@locked";
    private static final String PROP_EDITOR_LOCKED = "editor_locked";
    private static final String PROP_PREFAB_GENERATED = "@prefab_generated";
    private static final String PROP_PREFAB_SOURCE_SCENE = "@prefab_scene";
    private static final String PROP_PREFAB_SOURCE_NODE_ID = "@prefab_source_id";
    private static final String PROP_PREFAB_INSTANCE_ROOT = "@prefab_instance_root";

    private static final int MAX_PASSES = 16;

    private final HashMap<String, SceneCache> cacheBySceneId = new HashMap<>();

    public boolean syncAll(ServerScenes scenes) {
        if (scenes == null) {
            return false;
        }
        boolean anyChanged = false;
        for (ServerScene scene : scenes.allScenes()) {
            if (scene != null) {
                anyChanged |= syncScene(scenes, scene);
            }
        }
        return anyChanged;
    }

    public boolean syncScene(ServerScenes scenes, ServerScene scene) {
        if (scenes == null || scene == null) {
            return false;
        }
        SceneCache cache = cacheBySceneId.computeIfAbsent(scene.sceneId(), ignored -> new SceneCache());
        boolean anyChanged = false;
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            boolean changed = syncScenePass(scenes, scene, cache);
            anyChanged |= changed;
            if (!changed) {
                break;
            }
        }
        return anyChanged;
    }

    private boolean syncScenePass(ServerScenes scenes, ServerScene scene, SceneCache cache) {
        Engine engine = scene.engine();
        long graphRev = engine.graphRevision();
        if (graphRev != cache.lastGraphRevision) {
            cache.lastGraphRevision = graphRev;
            rescanInstances(scenes, scene, cache);
        }

        boolean anyChanged = false;
        for (InstanceState st : cache.instancesById.values()) {
            if (st == null || st.nodeId <= 0L) {
                continue;
            }
            Node instanceNode = engine.sceneTree().getNode(st.nodeId);
            if (instanceNode == null) {
                continue;
            }

            String refId = normalizeSceneRef(st.sourceSceneId);
            if (refId.isEmpty()) {
                anyChanged |= clearInstanceChildren(scene, instanceNode);
                st.lastSourceSceneRevision = Long.MIN_VALUE;
                continue;
            }

            if (wouldCreateSceneCycle(scene, instanceNode, refId)) {
                anyChanged |= clearInstanceChildren(scene, instanceNode);
                st.lastSourceSceneRevision = Long.MIN_VALUE;
                continue;
            }

            ServerScene source = scenes.get(refId);
            if (source == null) {
                anyChanged |= clearInstanceChildren(scene, instanceNode);
                st.lastSourceSceneRevision = Long.MIN_VALUE;
                continue;
            }

            long sourceRev = source.engine().sceneRevision();
            if (st.lastSourceSceneRevision == sourceRev && st.lastAppliedOk) {
                continue;
            }

            boolean changed = rebuildInstance(scenes, scene, source, instanceNode);
            anyChanged |= changed;
            if (changed) {
                st.lastSourceSceneRevision = sourceRev;
                st.lastAppliedOk = true;
            }
        }

        return anyChanged;
    }

    private void rescanInstances(ServerScenes scenes, ServerScene scene, SceneCache cache) {
        Objects.requireNonNull(scene, "scene");
        Engine engine = scene.engine();
        HashMap<Long, InstanceState> next = new HashMap<>();

        ArrayDeque<Node> stack = new ArrayDeque<>();
        stack.push(engine.sceneTree().root());

        while (!stack.isEmpty()) {
            Node node = stack.pop();
            if (node == null) {
                continue;
            }
            String typeId = engine.nodeTypes().typeIdFor(node);
            if (TYPE_SCENE_INSTANCE.equals(typeId)) {
                long nodeId = node.nodeId();
                String refId = normalizeSceneRef(node.getProperty(PROP_SCENE_ID));
                InstanceState prev = cache.instancesById.get(nodeId);
                InstanceState st = prev != null ? prev : new InstanceState(nodeId);
                if (!Objects.equals(st.sourceSceneId, refId)) {
                    st.sourceSceneId = refId;
                    st.lastSourceSceneRevision = Long.MIN_VALUE;
                    st.lastAppliedOk = false;
                }
                next.put(nodeId, st);
            }

            for (Node child : node.children()) {
                if (child != null) {
                    stack.push(child);
                }
            }
        }

        cache.instancesById = next;
    }

    private boolean rebuildInstance(ServerScenes scenes, ServerScene targetScene, ServerScene sourceScene, Node instanceNode) {
        if (targetScene == null || sourceScene == null || instanceNode == null) {
            return false;
        }

        String desiredX = defaulted(instanceNode.getProperty("x"), "0");
        String desiredY = defaulted(instanceNode.getProperty("y"), "0");
        String desiredZ = defaulted(instanceNode.getProperty("z"), "0");
        String desiredRx = defaulted(instanceNode.getProperty("rx"), "0");
        String desiredRy = defaulted(instanceNode.getProperty("ry"), "0");
        String desiredRz = defaulted(instanceNode.getProperty("rz"), "0");

        boolean hadCsgBefore = subtreeContainsCsg(instanceNode, targetScene.engine().nodeTypes());

        for (Node child : List.copyOf(instanceNode.children())) {
            instanceNode.removeChild(child);
        }

        NodeTypeRegistry sourceTypes = sourceScene.engine().nodeTypes();
        NodeTypeRegistry targetTypes = targetScene.engine().nodeTypes();
        for (Node child : sourceScene.engine().sceneTree().root().children()) {
            if (!shouldClone(child, sourceTypes)) {
                continue;
            }
            Node cloned = cloneSubtree(child, sourceTypes, targetTypes, sourceScene.sceneId(), instanceNode.nodeId());
            instanceNode.addChild(cloned);
        }

        // Apply the instance node's current transform to the newly created subtree (transient nodes are not persisted).
        instanceNode.setProperty("x", "0");
        instanceNode.setProperty("y", "0");
        instanceNode.setProperty("z", "0");
        instanceNode.setProperty("rx", "0");
        instanceNode.setProperty("ry", "0");
        instanceNode.setProperty("rz", "0");

        long batchId = SceneBatchIds.markRuntime((targetScene.engine().ticks() << 32) ^ System.nanoTime());
        ArrayList<SceneOp> ops = new ArrayList<>(6);
        ops.add(new SceneOp.SetProperty(instanceNode.nodeId(), "x", desiredX));
        ops.add(new SceneOp.SetProperty(instanceNode.nodeId(), "y", desiredY));
        ops.add(new SceneOp.SetProperty(instanceNode.nodeId(), "z", desiredZ));
        ops.add(new SceneOp.SetProperty(instanceNode.nodeId(), "rx", desiredRx));
        ops.add(new SceneOp.SetProperty(instanceNode.nodeId(), "ry", desiredRy));
        ops.add(new SceneOp.SetProperty(instanceNode.nodeId(), "rz", desiredRz));

        SceneOpAck ack = targetScene.applier().apply(new SceneOpBatch(batchId, true, List.copyOf(ops)));
        boolean ok = ack != null && ack.results() != null && ack.results().stream().allMatch(r -> r != null && r.ok());
        if (!ok && ack != null && ack.results() != null) {
            for (SceneOpResult r : ack.results()) {
                if (r != null && !r.ok()) {
                    System.err.println("[moud][instancer] transform apply failed scene=" + targetScene.sceneId()
                            + " instanceNodeId=" + instanceNode.nodeId()
                            + " targetId=" + r.targetId()
                            + " error=" + (r.message() == null ? r.error() : r.message()));
                }
            }
        }

        targetScene.engine().bumpGraphRevision();
        boolean hasCsgAfter = subtreeContainsCsg(instanceNode, targetScene.engine().nodeTypes());
        if (hadCsgBefore || hasCsgAfter) {
            targetScene.engine().bumpCsgRevision();
        }
        return true;
    }

    private boolean clearInstanceChildren(ServerScene scene, Node instanceNode) {
        if (scene == null || instanceNode == null) {
            return false;
        }
        if (instanceNode.children().isEmpty()) {
            return false;
        }
        boolean hadCsgBefore = subtreeContainsCsg(instanceNode, scene.engine().nodeTypes());
        for (Node child : List.copyOf(instanceNode.children())) {
            instanceNode.removeChild(child);
        }
        scene.engine().bumpGraphRevision();
        if (hadCsgBefore) {
            scene.engine().bumpCsgRevision();
        }
        scene.engine().bumpSceneRevision();
        return true;
    }

    private boolean wouldCreateSceneCycle(ServerScene topScene, Node instanceNode, String refSceneId) {
        if (topScene == null || instanceNode == null || refSceneId == null || refSceneId.isBlank()) {
            return false;
        }
        if (refSceneId.equals(topScene.sceneId())) {
            return true;
        }

        Engine engine = topScene.engine();
        Node cur = instanceNode.parent();
        while (cur != null) {
            if (TYPE_SCENE_INSTANCE.equals(engine.nodeTypes().typeIdFor(cur))) {
                String parentRef = normalizeSceneRef(cur.getProperty(PROP_SCENE_ID));
                if (refSceneId.equals(parentRef)) {
                    return true;
                }
            }
            cur = cur.parent();
        }
        return false;
    }

    private static Node cloneSubtree(Node source,
                                    NodeTypeRegistry sourceTypes,
                                    NodeTypeRegistry targetTypes,
                                    String sourceSceneId,
                                    long instanceRootId) {
        PlainNode node = new PlainNode(source.name() == null ? "" : source.name());

        for (Map.Entry<String, String> entry : source.properties().entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            node.setProperty(entry.getKey(), entry.getValue());
        }

        node.setProperty(PROP_TRANSIENT, "true");
        node.setProperty(PROP_LOCKED, "true");
        node.setProperty(PROP_EDITOR_LOCKED, "true");
        node.setProperty(PROP_PREFAB_GENERATED, "true");
        node.setProperty(PROP_PREFAB_SOURCE_SCENE, sourceSceneId);
        node.setProperty(PROP_PREFAB_SOURCE_NODE_ID, Long.toString(source.nodeId()));
        node.setProperty(PROP_PREFAB_INSTANCE_ROOT, Long.toString(instanceRootId));

        String typeId = sourceTypes.typeIdFor(source);
        targetTypes.applyDefaults(node, typeId);

        for (Node child : source.children()) {
            if (shouldClone(child, sourceTypes)) {
                node.addChild(cloneSubtree(child, sourceTypes, targetTypes, sourceSceneId, instanceRootId));
            }
        }

        return node;
    }

    private static boolean shouldClone(Node node, NodeTypeRegistry types) {
        if (node == null) {
            return false;
        }
        String typeId = types.typeIdFor(node);
        if (EXCLUDED_TYPE_IDS.contains(typeId)) {
            return false;
        }
        if (node.name() != null && node.name().startsWith(LEGACY_PLAYER_PREFIX)) {
            return false;
        }
        return !(isTrue(node.getProperty(PROP_RUNTIME))
                || isTrue(node.getProperty(PROP_TRANSIENT))
                || isTrue(node.getProperty(PROP_RUNTIME_ONLY)));
    }

    private static boolean subtreeContainsCsg(Node node, NodeTypeRegistry types) {
        if (node == null) {
            return false;
        }
        if ("CSGBlock".equals(types.typeIdFor(node))) {
            return true;
        }
        for (Node child : node.children()) {
            if (subtreeContainsCsg(child, types)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeSceneRef(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        if (s.startsWith("res://")) {
            int slash = s.lastIndexOf('/');
            String filename = slash >= 0 ? s.substring(slash + 1) : s.substring("res://".length());
            s = filename;
        } else if (s.contains("/")) {
            int slash = s.lastIndexOf('/');
            s = s.substring(slash + 1);
        }
        if (s.endsWith(".moud.scene")) {
            s = s.substring(0, s.length() - ".moud.scene".length());
        }
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isTrue(String v) {
        if (v == null) {
            return false;
        }
        String s = v.trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s);
    }

    private static String defaulted(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static final class SceneCache {
        long lastGraphRevision = Long.MIN_VALUE;
        Map<Long, InstanceState> instancesById = new HashMap<>();
    }

    private static final class InstanceState {
        final long nodeId;
        String sourceSceneId = "";
        long lastSourceSceneRevision = Long.MIN_VALUE;
        boolean lastAppliedOk;

        InstanceState(long nodeId) {
            this.nodeId = nodeId;
        }
    }
}
