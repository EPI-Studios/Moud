package com.moud.server.minestom.engine;

import com.moud.core.NodeTypeRegistry;
import com.moud.core.scene.Node;
import com.moud.core.scene.SceneTree;
import com.moud.net.protocol.SceneSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class Engine {
    private final SceneTree sceneTree;
    private final NodeTypeRegistry nodeTypes;
    private final AtomicLong ticks = new AtomicLong();
    private final AtomicLong sceneRevision = new AtomicLong();
    private final AtomicLong csgRevision = new AtomicLong();
    private volatile long lastDumpTick = -1;

    public Engine(Node root) {
        this(root, new NodeTypeRegistry());
    }

    public Engine(Node root, NodeTypeRegistry nodeTypes) {
        this.sceneTree = new SceneTree(Objects.requireNonNull(root));
        this.nodeTypes = Objects.requireNonNull(nodeTypes);
    }

    private static void dumpNode(StringBuilder sb, Node node, int depth) {
        sb.append("  ".repeat(Math.max(0, depth)));
        sb.append(node.name()).append("  ").append(node.path()).append('\n');
        for (Node child : node.children()) {
            dumpNode(sb, child, depth + 1);
        }
    }

    public SceneTree sceneTree() {
        return sceneTree;
    }

    public NodeTypeRegistry nodeTypes() {
        return nodeTypes;
    }

    public long ticks() {
        return ticks.get();
    }

    public long sceneRevision() {
        return sceneRevision.get();
    }

    public void bumpSceneRevision() {
        sceneRevision.incrementAndGet();
    }

    public long csgRevision() {
        return csgRevision.get();
    }

    public void bumpCsgRevision() {
        csgRevision.incrementAndGet();
    }

    public void tick(double dtSeconds) {
        ticks.incrementAndGet();
        sceneTree.tick(dtSeconds);
    }

    public SceneSnapshot snapshot(long requestId) {
        List<SceneSnapshot.NodeSnapshot> nodes = new ArrayList<>();
        snapshotNode(sceneTree.root(), nodes);
        return new SceneSnapshot(requestId, sceneRevision(), List.copyOf(nodes));
    }

    public String dumpScene() {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("SceneTree ticks=").append(ticks()).append('\n');
        dumpNode(sb, sceneTree.root(), 0);
        lastDumpTick = ticks();
        return sb.toString();
    }

    public long lastDumpTick() {
        return lastDumpTick;
    }

    private void snapshotNode(Node node, List<SceneSnapshot.NodeSnapshot> out) {
        List<SceneSnapshot.Property> props = new ArrayList<>(node.properties().size());
        for (Map.Entry<String, String> entry : node.properties().entrySet()) {
            props.add(new SceneSnapshot.Property(entry.getKey(), entry.getValue()));
        }
        long parentId = node.parent() == null ? 0L : node.parent().nodeId();
        out.add(new SceneSnapshot.NodeSnapshot(
                node.nodeId(),
                parentId,
                node.name(),
                nodeTypes.typeIdFor(node),
                List.copyOf(props)
        ));
        for (Node child : node.children()) {
            snapshotNode(child, out);
        }
    }
}
