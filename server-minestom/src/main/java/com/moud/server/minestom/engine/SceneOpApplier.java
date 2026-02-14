package com.moud.server.minestom.engine;

import com.moud.core.scene.Node;
import com.moud.core.scene.PlainNode;
import com.moud.net.protocol.*;

import java.util.*;
import java.util.function.Consumer;

public final class SceneOpApplier {
    private static final Set<String> CSG_KEYS = Set.of("x", "y", "z", "rx", "ry", "rz", "sx", "sy", "sz", "block", "@type");
    private final Engine engine;
    private Consumer<String> logSink = s -> {
    };

    public SceneOpApplier(Engine engine) {
        this.engine = Objects.requireNonNull(engine);
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

    public void setLogSink(Consumer<String> logSink) {
        this.logSink = Objects.requireNonNull(logSink);
    }

    public SceneOpAck apply(SceneOpBatch batch) {
        List<SceneOpResult> results = new ArrayList<>(batch.ops().size());
        boolean anySceneChanged = false;
        boolean anyCsgChanged = false;

        if (batch.atomic()) {
            List<SceneOpResult> validation = validateAtomic(batch.ops());
            boolean ok = validation.stream().allMatch(SceneOpResult::ok);
            if (!ok) {
                return new SceneOpAck(batch.batchId(), engine.sceneRevision(), List.copyOf(validation));
            }
        }

        for (SceneOp op : batch.ops()) {
            ApplyOutcome out = applyOne(op);
            results.add(out.result);
            anySceneChanged |= out.sceneChanged;
            anyCsgChanged |= out.csgChanged;
        }

        if (anySceneChanged) {
            engine.bumpSceneRevision();
        }
        if (anyCsgChanged) {
            engine.bumpCsgRevision();
            logSink.accept("SceneOp: CSG changed; csgRevision=" + engine.csgRevision());
        }
        return new SceneOpAck(batch.batchId(), engine.sceneRevision(), List.copyOf(results));
    }

    private List<SceneOpResult> validateAtomic(List<SceneOp> ops) {
        Map<Long, Set<String>> reservedNamesByParent = new HashMap<>();
        List<SceneOpResult> results = new ArrayList<>(ops.size());
        for (SceneOp op : ops) {
            results.add(validateOne(op, reservedNamesByParent));
        }
        return results;
    }

    private SceneOpResult validateOne(SceneOp op, Map<Long, Set<String>> reservedNamesByParent) {
        return switch (op) {
            case SceneOp.CreateNode createNode -> {
                Node parent = engine.sceneTree().getNode(createNode.parentId());
                if (parent == null) {
                    logSink.accept("SceneOp: parent not found: " + createNode.parentId());
                    yield SceneOpResult.fail(createNode.parentId(), SceneOpError.NOT_FOUND, "parent not found");
                }
                Set<String> reserved = reservedNamesByParent.computeIfAbsent(parent.nodeId(), id -> {
                    Set<String> set = new HashSet<>();
                    for (Node child : parent.children()) {
                        set.add(child.name());
                    }
                    return set;
                });
                if (createNode.name() == null || createNode.name().isBlank()) {
                    yield SceneOpResult.fail(parent.nodeId(), SceneOpError.INVALID, "name empty");
                }
                if (reserved.contains(createNode.name())) {
                    yield SceneOpResult.fail(parent.nodeId(), SceneOpError.ALREADY_EXISTS, "child already exists");
                }
                reserved.add(createNode.name());
                yield SceneOpResult.ok(parent.nodeId());
            }
            case SceneOp.QueueFree queueFree -> {
                Node node = engine.sceneTree().getNode(queueFree.nodeId());
                if (node == null) {
                    logSink.accept("SceneOp: node not found: " + queueFree.nodeId());
                    yield SceneOpResult.fail(queueFree.nodeId(), SceneOpError.NOT_FOUND, "node not found");
                }
                if (node.parent() == null) {
                    logSink.accept("SceneOp: refusing to free root");
                    yield SceneOpResult.fail(queueFree.nodeId(), SceneOpError.INVALID, "cannot free root");
                }
                yield SceneOpResult.ok(queueFree.nodeId());
            }
            case SceneOp.Rename rename -> {
                Node node = engine.sceneTree().getNode(rename.nodeId());
                if (node == null) {
                    logSink.accept("SceneOp: node not found: " + rename.nodeId());
                    yield SceneOpResult.fail(rename.nodeId(), SceneOpError.NOT_FOUND, "node not found");
                }
                if (rename.newName() == null || rename.newName().isBlank()) {
                    yield SceneOpResult.fail(rename.nodeId(), SceneOpError.INVALID, "name empty");
                }
                Node parent = node.parent();
                if (parent != null) {
                    Set<String> reserved = reservedNamesByParent.computeIfAbsent(parent.nodeId(), id -> {
                        Set<String> set = new HashSet<>();
                        for (Node child : parent.children()) {
                            set.add(child.name());
                        }
                        return set;
                    });
                    if (reserved.contains(rename.newName()) && !rename.newName().equals(node.name())) {
                        yield SceneOpResult.fail(rename.nodeId(), SceneOpError.ALREADY_EXISTS, "sibling already exists");
                    }
                    reserved.add(rename.newName());
                }
                yield SceneOpResult.ok(rename.nodeId());
            }
            case SceneOp.SetProperty setProperty -> {
                Node node = engine.sceneTree().getNode(setProperty.nodeId());
                if (node == null) {
                    yield SceneOpResult.fail(setProperty.nodeId(), SceneOpError.NOT_FOUND, "node not found");
                }
                if (setProperty.key() == null || setProperty.key().isBlank()) {
                    yield SceneOpResult.fail(setProperty.nodeId(), SceneOpError.INVALID, "key empty");
                }
                String typeId = engine.nodeTypes().typeIdFor(node);
                var vr = engine.nodeTypes().validateSetProperty(typeId, setProperty.key(), setProperty.value());
                if (!vr.ok()) {
                    yield SceneOpResult.fail(setProperty.nodeId(), SceneOpError.INVALID, vr.message());
                }
                yield SceneOpResult.ok(setProperty.nodeId());
            }
            case SceneOp.RemoveProperty removeProperty -> {
                Node node = engine.sceneTree().getNode(removeProperty.nodeId());
                if (node == null) {
                    yield SceneOpResult.fail(removeProperty.nodeId(), SceneOpError.NOT_FOUND, "node not found");
                }
                if (removeProperty.key() == null || removeProperty.key().isBlank()) {
                    yield SceneOpResult.fail(removeProperty.nodeId(), SceneOpError.INVALID, "key empty");
                }
                String typeId = engine.nodeTypes().typeIdFor(node);
                var vr = engine.nodeTypes().validateRemoveProperty(typeId, removeProperty.key());
                if (!vr.ok()) {
                    yield SceneOpResult.fail(removeProperty.nodeId(), SceneOpError.INVALID, vr.message());
                }
                yield SceneOpResult.ok(removeProperty.nodeId());
            }
            case SceneOp.Reparent reparent -> {
                Node node = engine.sceneTree().getNode(reparent.nodeId());
                if (node == null) {
                    yield SceneOpResult.fail(reparent.nodeId(), SceneOpError.NOT_FOUND, "node not found");
                }
                if (node.parent() == null) {
                    yield SceneOpResult.fail(reparent.nodeId(), SceneOpError.INVALID, "cannot reparent root");
                }
                Node newParent = engine.sceneTree().getNode(reparent.newParentId());
                if (newParent == null) {
                    yield SceneOpResult.fail(reparent.newParentId(), SceneOpError.NOT_FOUND, "new parent not found");
                }
                if (wouldCreateCycle(node, newParent)) {
                    yield SceneOpResult.fail(reparent.nodeId(), SceneOpError.INVALID, "cycle");
                }
                yield SceneOpResult.ok(reparent.nodeId());
            }
        };
    }

    private ApplyOutcome applyOne(SceneOp op) {
        return switch (op) {
            case SceneOp.CreateNode createNode -> {
                Node parent = engine.sceneTree().getNode(createNode.parentId());
                if (parent == null) {
                    yield new ApplyOutcome(SceneOpResult.fail(createNode.parentId(), SceneOpError.NOT_FOUND, "parent not found"), false);
                }
                if (parent.findChild(createNode.name()) != null) {
                    yield new ApplyOutcome(SceneOpResult.fail(parent.nodeId(), SceneOpError.ALREADY_EXISTS, "child already exists"), false);
                }
                PlainNode child = new PlainNode(createNode.name());
                engine.nodeTypes().applyDefaults(child, createNode.typeId());
                parent.addChild(child);
                yield new ApplyOutcome(SceneOpResult.created(parent.nodeId(), child.nodeId()), true, "CSGBlock".equals(createNode.typeId()));
            }
            case SceneOp.QueueFree queueFree -> {
                Node node = engine.sceneTree().getNode(queueFree.nodeId());
                if (node == null) {
                    yield new ApplyOutcome(SceneOpResult.fail(queueFree.nodeId(), SceneOpError.NOT_FOUND, "node not found"), false);
                }
                if (node.parent() == null) {
                    yield new ApplyOutcome(SceneOpResult.fail(queueFree.nodeId(), SceneOpError.INVALID, "cannot free root"), false);
                }
                boolean affectsCsg = subtreeContainsCsg(node);
                node.queueFree();
                yield new ApplyOutcome(SceneOpResult.ok(queueFree.nodeId()), true, affectsCsg);
            }
            case SceneOp.Rename rename -> {
                Node node = engine.sceneTree().getNode(rename.nodeId());
                if (node == null) {
                    yield new ApplyOutcome(SceneOpResult.fail(rename.nodeId(), SceneOpError.NOT_FOUND, "node not found"), false);
                }
                if (node.parent() != null && node.parent().findChild(rename.newName()) != null && !rename.newName().equals(node.name())) {
                    yield new ApplyOutcome(SceneOpResult.fail(rename.nodeId(), SceneOpError.ALREADY_EXISTS, "sibling already exists"), false);
                }
                node.setName(rename.newName());
                yield new ApplyOutcome(SceneOpResult.ok(rename.nodeId()), true);
            }
            case SceneOp.SetProperty setProperty -> {
                Node node = engine.sceneTree().getNode(setProperty.nodeId());
                if (node == null) {
                    yield new ApplyOutcome(SceneOpResult.fail(setProperty.nodeId(), SceneOpError.NOT_FOUND, "node not found"), false);
                }
                if (setProperty.key() == null || setProperty.key().isBlank()) {
                    yield new ApplyOutcome(SceneOpResult.fail(setProperty.nodeId(), SceneOpError.INVALID, "key empty"), false);
                }
                String typeId = engine.nodeTypes().typeIdFor(node);
                var vr = engine.nodeTypes().validateSetProperty(typeId, setProperty.key(), setProperty.value());
                if (!vr.ok()) {
                    yield new ApplyOutcome(SceneOpResult.fail(setProperty.nodeId(), SceneOpError.INVALID, vr.message()), false);
                }
                boolean affectsCsg = affectsCsg(node, setProperty.key());
                node.setProperty(setProperty.key(), setProperty.value());
                yield new ApplyOutcome(SceneOpResult.ok(setProperty.nodeId()), true, affectsCsg);
            }
            case SceneOp.RemoveProperty removeProperty -> {
                Node node = engine.sceneTree().getNode(removeProperty.nodeId());
                if (node == null) {
                    yield new ApplyOutcome(SceneOpResult.fail(removeProperty.nodeId(), SceneOpError.NOT_FOUND, "node not found"), false);
                }
                if (removeProperty.key() == null || removeProperty.key().isBlank()) {
                    yield new ApplyOutcome(SceneOpResult.fail(removeProperty.nodeId(), SceneOpError.INVALID, "key empty"), false);
                }
                String typeId = engine.nodeTypes().typeIdFor(node);
                var vr = engine.nodeTypes().validateRemoveProperty(typeId, removeProperty.key());
                if (!vr.ok()) {
                    yield new ApplyOutcome(SceneOpResult.fail(removeProperty.nodeId(), SceneOpError.INVALID, vr.message()), false);
                }
                boolean affectsCsg = affectsCsg(node, removeProperty.key());
                node.removeProperty(removeProperty.key());
                yield new ApplyOutcome(SceneOpResult.ok(removeProperty.nodeId()), true, affectsCsg);
            }
            case SceneOp.Reparent reparent -> {
                Node node = engine.sceneTree().getNode(reparent.nodeId());
                if (node == null) {
                    yield new ApplyOutcome(SceneOpResult.fail(reparent.nodeId(), SceneOpError.NOT_FOUND, "node not found"), false);
                }
                if (node.parent() == null) {
                    yield new ApplyOutcome(SceneOpResult.fail(reparent.nodeId(), SceneOpError.INVALID, "cannot reparent root"), false);
                }
                Node newParent = engine.sceneTree().getNode(reparent.newParentId());
                if (newParent == null) {
                    yield new ApplyOutcome(SceneOpResult.fail(reparent.newParentId(), SceneOpError.NOT_FOUND, "new parent not found"), false);
                }
                boolean ok = engine.sceneTree().reparent(reparent.nodeId(), reparent.newParentId(), reparent.index());
                if (!ok) {
                    yield new ApplyOutcome(SceneOpResult.fail(reparent.nodeId(), SceneOpError.INVALID, "reparent failed"), false);
                }
                yield new ApplyOutcome(SceneOpResult.ok(reparent.nodeId()), true);
            }
        };
    }

    private boolean affectsCsg(Node node, String key) {
        if (node == null || key == null) {
            return false;
        }
        if (!CSG_KEYS.contains(key)) {
            return false;
        }
        if ("@type".equals(key)) {
            return true;
        }
        return "CSGBlock".equals(engine.nodeTypes().typeIdFor(node));
    }

    private boolean subtreeContainsCsg(Node node) {
        if (node == null) {
            return false;
        }
        if ("CSGBlock".equals(engine.nodeTypes().typeIdFor(node))) {
            return true;
        }
        for (Node child : node.children()) {
            if (subtreeContainsCsg(child)) {
                return true;
            }
        }
        return false;
    }

    private record ApplyOutcome(SceneOpResult result, boolean sceneChanged, boolean csgChanged) {
        private ApplyOutcome(SceneOpResult result, boolean sceneChanged) {
            this(result, sceneChanged, false);
        }
    }

}
