package com.moud.server.minestom.engine;

import com.moud.core.NodeTypeDef;
import com.moud.core.math.Quat;
import com.moud.core.math.Vec3;
import com.moud.core.scene.Node;
import com.moud.core.scene.PlainNode;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneOpError;
import com.moud.net.protocol.SceneOpResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class SceneOpApplier {
    private static final Set<String> CSG_KEYS = Set.of(
            "x", "y", "z", "rx", "ry", "rz", "sx", "sy", "sz",
            "block", "solid", "@type"
    );
    private static final Set<String> PHYSICS_KEYS = Set.of(
            "x", "y", "z", "rx", "ry", "rz", "sx", "sy", "sz",
            "solid", "@type",
            "shape", "radius", "height", "enabled",
            "mass", "freeze",
            "linear_damping", "angular_damping", "gravity_scale"
    );
    private static final Set<String> FILTER_KEYS = Set.of("collision_layer", "collision_mask");
    private static final Set<String> POSITION_KEYS = Set.of("x", "y", "z");
    private static final Set<String> ROTATION_KEYS = Set.of("rx", "ry", "rz");
    private static final Set<String> SCALE_KEYS = Set.of("sx", "sy", "sz");
    private static final String PROP_PREFAB_GENERATED = "@prefab_generated";
    private static final String TYPE_SCENE_INSTANCE = "SceneInstance3D";
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
        boolean anyPhysicsChanged = false;
        boolean anyFilterChanged = false;
        boolean anyGraphChanged = false;
        boolean runtimeBatch = SceneBatchIds.isRuntime(batch.batchId());

        if (batch.atomic()) {
            List<SceneOpResult> validation = validateAtomic(batch.ops(), runtimeBatch);
            boolean ok = validation.stream().allMatch(SceneOpResult::ok);
            if (!ok) {
                return new SceneOpAck(batch.batchId(), engine.sceneRevision(), List.copyOf(validation));
            }
        }

        List<SceneOp> ops = batch.ops();
        for (int i = 0; i < ops.size(); i++) {
            SceneOp op = ops.get(i);

            ApplyOutcome out = applyOne(op, runtimeBatch);
            results.add(out.result);
            anySceneChanged |= out.sceneChanged;
            anyCsgChanged |= out.csgChanged;
            anyPhysicsChanged |= out.physicsChanged;
            anyFilterChanged |= out.filterChanged;
            anyGraphChanged |= out.sceneChanged && isGraphOp(op);
        }

        if (anySceneChanged) {
            engine.bumpSceneRevision();
        }
        if (anyGraphChanged) {
            engine.bumpSceneRevision();
        }
        if (anyCsgChanged) {
            engine.bumpCsgRevision();
            logSink.accept("SceneOp: CSG changed; csgRevision=" + engine.csgRevision());
        }
        if (anyPhysicsChanged) {
            engine.bumpPhysicsRevision();
        }
        if (anyFilterChanged) {
            engine.bumpCollisionFilterRevision();
        }
        return new SceneOpAck(batch.batchId(), engine.sceneRevision(), List.copyOf(results));
    }

    private boolean isTransformKey(String key) {
        return POSITION_KEYS.contains(key) || ROTATION_KEYS.contains(key) || SCALE_KEYS.contains(key);
    }

    private boolean isGraphOp(SceneOp op) {
        if (op instanceof SceneOp.SetProperty sp) {
            return !isTransformKey(sp.key());
        }
        if (op instanceof SceneOp.RemoveProperty rp) {
            return !isTransformKey(rp.key());
        }
        return true;
    }

    private List<SceneOpResult> validateAtomic(List<SceneOp> ops, boolean runtimeBatch) {
        Map<Long, Set<String>> reservedNamesByParent = new HashMap<>();
        List<SceneOpResult> results = new ArrayList<>(ops.size());
        for (SceneOp op : ops) {
            results.add(validateOne(op, reservedNamesByParent, runtimeBatch));
        }
        return results;
    }

    private SceneOpResult validateOne(SceneOp op, Map<Long, Set<String>> reservedNamesByParent, boolean runtimeBatch) {
        return switch (op) {
            case SceneOp.CreateNode createNode -> {
                Node parent = engine.sceneTree().getNode(createNode.parentId());
                if (parent == null) {
                    logSink.accept("SceneOp: parent not found: " + createNode.parentId());
                    yield SceneOpResult.fail(createNode.parentId(), SceneOpError.NOT_FOUND, "parent not found");
                }
                if (isPrefabGenerated(parent)) {
                    yield SceneOpResult.fail(createNode.parentId(), SceneOpError.INVALID, "cannot add children to prefab-generated nodes");
                }
                if (isSceneInstance(parent)) {
                    yield SceneOpResult.fail(createNode.parentId(), SceneOpError.INVALID, "cannot add children to SceneInstance3D");
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
                if (isPrefabGenerated(node)) {
                    yield SceneOpResult.fail(queueFree.nodeId(), SceneOpError.INVALID, "prefab-generated nodes are read-only");
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
                if (isPrefabGenerated(node) && !runtimeBatch) {
                    yield SceneOpResult.fail(rename.nodeId(), SceneOpError.INVALID, "prefab-generated nodes are read-only");
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
                if (isPrefabGenerated(node)) {
                    yield SceneOpResult.fail(reparent.nodeId(), SceneOpError.INVALID, "prefab-generated nodes are read-only");
                }
                if (node.parent() == null) {
                    yield SceneOpResult.fail(reparent.nodeId(), SceneOpError.INVALID, "cannot reparent root");
                }
                Node newParent = engine.sceneTree().getNode(reparent.newParentId());
                if (newParent == null) {
                    yield SceneOpResult.fail(reparent.newParentId(), SceneOpError.NOT_FOUND, "new parent not found");
                }
                if (isPrefabGenerated(newParent)) {
                    yield SceneOpResult.fail(reparent.newParentId(), SceneOpError.INVALID, "cannot reparent into prefab-generated nodes");
                }
                if (isSceneInstance(newParent)) {
                    yield SceneOpResult.fail(reparent.newParentId(), SceneOpError.INVALID, "cannot reparent into SceneInstance3D");
                }
                if (wouldCreateCycle(node, newParent)) {
                    yield SceneOpResult.fail(reparent.nodeId(), SceneOpError.INVALID, "cycle");
                }
                yield SceneOpResult.ok(reparent.nodeId());
            }
        };
    }

    private ApplyOutcome applyOne(SceneOp op, boolean runtimeBatch) {
        return switch (op) {
            case SceneOp.CreateNode createNode -> {
                Node parent = engine.sceneTree().getNode(createNode.parentId());
                if (parent == null) {
                    yield new ApplyOutcome(SceneOpResult.fail(createNode.parentId(), SceneOpError.NOT_FOUND, "parent not found"), false);
                }
                if (isPrefabGenerated(parent)) {
                    yield new ApplyOutcome(SceneOpResult.fail(createNode.parentId(), SceneOpError.INVALID, "cannot add children to prefab-generated nodes"), false);
                }
                if (isSceneInstance(parent)) {
                    yield new ApplyOutcome(SceneOpResult.fail(createNode.parentId(), SceneOpError.INVALID, "cannot add children to SceneInstance3D"), false);
                }
                if (parent.findChild(createNode.name()) != null) {
                    yield new ApplyOutcome(SceneOpResult.fail(parent.nodeId(), SceneOpError.ALREADY_EXISTS, "child already exists"), false);
                }
                PlainNode child = new PlainNode(createNode.name());
                engine.nodeTypes().applyDefaults(child, createNode.typeId());
                parent.addChild(child);
                String typeId = createNode.typeId();
                boolean csgChanged = isCsgTypeId(typeId);
                boolean physicsChanged = csgChanged || isPhysicsBodyTypeId(typeId);
                yield new ApplyOutcome(SceneOpResult.created(parent.nodeId(), child.nodeId()), true,
                        csgChanged, physicsChanged, false);
            }
            case SceneOp.QueueFree queueFree -> {
                Node node = engine.sceneTree().getNode(queueFree.nodeId());
                if (node == null) {
                    yield new ApplyOutcome(SceneOpResult.fail(queueFree.nodeId(), SceneOpError.NOT_FOUND, "node not found"), false);
                }
                if (isPrefabGenerated(node)) {
                    yield new ApplyOutcome(SceneOpResult.fail(queueFree.nodeId(), SceneOpError.INVALID, "prefab-generated nodes are read-only"), false);
                }
                if (node.parent() == null) {
                    yield new ApplyOutcome(SceneOpResult.fail(queueFree.nodeId(), SceneOpError.INVALID, "cannot free root"), false);
                }
                boolean affectsCsg = subtreeContainsCsg(node);
                boolean affectsPhysics = subtreeContainsPhysics(node);
                node.queueFree();
                yield new ApplyOutcome(SceneOpResult.ok(queueFree.nodeId()), true, affectsCsg, affectsPhysics, false);
            }
            case SceneOp.Rename rename -> {
                Node node = engine.sceneTree().getNode(rename.nodeId());
                if (node == null) {
                    yield new ApplyOutcome(SceneOpResult.fail(rename.nodeId(), SceneOpError.NOT_FOUND, "node not found"), false);
                }
                if (isPrefabGenerated(node) && !runtimeBatch) {
                    yield new ApplyOutcome(SceneOpResult.fail(rename.nodeId(), SceneOpError.INVALID, "prefab-generated nodes are read-only"), false);
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
                String key = setProperty.key();
                String value = setProperty.value();

                boolean affectsCsg = affectsCsg(node, key);
                boolean affectsPhysics = affectsPhysics(node, key);
                boolean affectsFilter = affectsCollisionFilter(node, key);
                if (isTransformKey(key) || "@inherit_transform".equals(key)) {
                    affectsCsg |= subtreeContainsCsgInheriting(node);
                    affectsPhysics |= subtreeContainsPhysicsInheriting(node);
                }

                node.setProperty(key, value);
                yield new ApplyOutcome(SceneOpResult.ok(setProperty.nodeId()), true, affectsCsg, affectsPhysics, affectsFilter);
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
                String key = removeProperty.key();
                boolean affectsCsg = affectsCsg(node, key);
                boolean affectsPhysics = affectsPhysics(node, key);
                boolean affectsFilter = affectsCollisionFilter(node, key);
                if (isTransformKey(key) || "@inherit_transform".equals(key)) {
                    affectsCsg |= subtreeContainsCsgInheriting(node);
                    affectsPhysics |= subtreeContainsPhysicsInheriting(node);
                }
                node.removeProperty(key);
                yield new ApplyOutcome(SceneOpResult.ok(removeProperty.nodeId()), true, affectsCsg, affectsPhysics, affectsFilter);
            }
            case SceneOp.Reparent reparent -> {
                Node node = engine.sceneTree().getNode(reparent.nodeId());
                if (node == null) {
                    yield new ApplyOutcome(SceneOpResult.fail(reparent.nodeId(), SceneOpError.NOT_FOUND, "node not found"), false);
                }
                if (isPrefabGenerated(node)) {
                    yield new ApplyOutcome(SceneOpResult.fail(reparent.nodeId(), SceneOpError.INVALID, "prefab-generated nodes are read-only"), false);
                }
                if (node.parent() == null) {
                    yield new ApplyOutcome(SceneOpResult.fail(reparent.nodeId(), SceneOpError.INVALID, "cannot reparent root"), false);
                }
                Node newParent = engine.sceneTree().getNode(reparent.newParentId());
                if (newParent == null) {
                    yield new ApplyOutcome(SceneOpResult.fail(reparent.newParentId(), SceneOpError.NOT_FOUND, "new parent not found"), false);
                }
                if (isPrefabGenerated(newParent)) {
                    yield new ApplyOutcome(SceneOpResult.fail(reparent.newParentId(), SceneOpError.INVALID, "cannot reparent into prefab-generated nodes"), false);
                }
                if (isSceneInstance(newParent)) {
                    yield new ApplyOutcome(SceneOpResult.fail(reparent.newParentId(), SceneOpError.INVALID, "cannot reparent into SceneInstance3D"), false);
                }
                boolean ok = engine.sceneTree().reparent(reparent.nodeId(), reparent.newParentId(), reparent.index());
                if (!ok) {
                    yield new ApplyOutcome(SceneOpResult.fail(reparent.nodeId(), SceneOpError.INVALID, "reparent failed"), false);
                }
                boolean affectsCsg = shouldInheritTransform(node) && subtreeContainsCsgInheriting(node);
                boolean affectsPhysics = shouldInheritTransform(node) && subtreeContainsPhysicsInheriting(node);
                yield new ApplyOutcome(SceneOpResult.ok(reparent.nodeId()), true, affectsCsg, affectsPhysics, false);
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
        String typeId = engine.nodeTypes().typeIdFor(node);
        return isCsgTypeId(typeId);
    }

    private boolean affectsPhysics(Node node, String key) {
        if (node == null || key == null) {
            return false;
        }
        if ("@type".equals(key)) {
            return true;
        }
        if (!PHYSICS_KEYS.contains(key)) {
            return false;
        }
        String typeId = engine.nodeTypes().typeIdFor(node);
        return isCsgTypeId(typeId) || isPhysicsBodyTypeId(typeId);
    }

    private boolean affectsCollisionFilter(Node node, String key) {
        if (node == null || key == null) {
            return false;
        }
        if (!FILTER_KEYS.contains(key)) {
            return false;
        }
        String typeId = engine.nodeTypes().typeIdFor(node);
        return isCsgTypeId(typeId) || isPhysicsBodyTypeId(typeId);
    }

    private boolean subtreeContainsCsg(Node node) {
        if (node == null) {
            return false;
        }
        String typeId = engine.nodeTypes().typeIdFor(node);
        if ("CSGBlock".equals(typeId) || "CSGBox".equals(typeId)) {
            return true;
        }
        for (Node child : node.children()) {
            if (subtreeContainsCsg(child)) {
                return true;
            }
        }
        return false;
    }

    private boolean subtreeContainsPhysics(Node node) {
        if (node == null) {
            return false;
        }
        String typeId = engine.nodeTypes().typeIdFor(node);
        if (isCsgTypeId(typeId) || isPhysicsBodyTypeId(typeId)) {
            return true;
        }
        for (Node child : node.children()) {
            if (subtreeContainsPhysics(child)) {
                return true;
            }
        }
        return false;
    }

    private boolean subtreeContainsCsgInheriting(Node node) {
        if (node == null) {
            return false;
        }
        String typeId = engine.nodeTypes().typeIdFor(node);
        if ("CSGBlock".equals(typeId) || "CSGBox".equals(typeId)) {
            return true;
        }
        for (Node child : node.children()) {
            if (child == null) {
                continue;
            }
            if (!shouldInheritTransform(child)) {
                continue;
            }
            if (subtreeContainsCsgInheriting(child)) {
                return true;
            }
        }
        return false;
    }

    private boolean subtreeContainsPhysicsInheriting(Node node) {
        if (node == null) {
            return false;
        }
        String typeId = engine.nodeTypes().typeIdFor(node);
        if (isCsgTypeId(typeId) || isPhysicsBodyTypeId(typeId)) {
            return true;
        }
        for (Node child : node.children()) {
            if (child == null) {
                continue;
            }
            if (!shouldInheritTransform(child)) {
                continue;
            }
            if (subtreeContainsPhysicsInheriting(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCsgTypeId(String typeId) {
        return "CSGBlock".equals(typeId) || "CSGBox".equals(typeId);
    }

    private static boolean isPhysicsBodyTypeId(String typeId) {
        return "StaticBody3D".equals(typeId) || "RigidBody3D".equals(typeId);
    }

    private static boolean shouldInheritTransform(Node node) {
        if (node == null) {
            return false;
        }
        String v = node.getProperty("@inherit_transform");
        if (v == null || v.isBlank()) {
            return true;
        }
        String s = v.trim().toLowerCase();
        return !("false".equals(s) || "0".equals(s));
    }

    private static float parseFloat(String value, float fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            float v = Float.parseFloat(value.trim());
            return Float.isFinite(v) ? v : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String trimFloat(float v) {
        if (!Float.isFinite(v)) {
            return "0";
        }
        if (Math.abs(v - Math.round(v)) < 1e-6f) {
            return Integer.toString(Math.round(v));
        }
        return Float.toString(v);
    }

    private boolean applyPositionDeltaToDescendants(Node node, String key, float delta) {
        boolean changed = false;
        for (Node child : node.children()) {
            if (child == null) {
                continue;
            }
            if (nodeAcceptsKey(child, key)) {
                float before = parseFloat(child.getProperty(key), 0.0f);
                float after = before + delta;
                child.setProperty(key, trimFloat(after));
                changed = true;
            }

            changed |= applyPositionDeltaToDescendants(child, key, delta);
        }
        return changed;
    }

    private boolean applyRotationDeltaToDescendants(Node node, Vec3 pivot, Quat delta) {
        boolean changed = false;
        for (Node child : node.children()) {
            if (child == null) {
                continue;
            }

            changed |= applyRotationDeltaToNode(child, pivot, delta);
            changed |= applyRotationDeltaToDescendants(child, pivot, delta);
        }
        return changed;
    }

    private boolean applyRotationDeltaToNode(Node node, Vec3 pivot, Quat delta) {
        boolean changed = false;

        if (nodeAcceptsKey(node, "x") && nodeAcceptsKey(node, "y") && nodeAcceptsKey(node, "z")) {
            NodePose pose = readPose(node);
            Vec3 offset = pose.pivot.sub(pivot);
            Vec3 rotated = delta.rotate(offset);
            Vec3 newPivot = pivot.add(rotated);
            writePivot(node, newPivot, pose.size);
            changed = true;
        }

        if (nodeAcceptsKey(node, "rx") && nodeAcceptsKey(node, "ry") && nodeAcceptsKey(node, "rz")) {
            float rx = parseFloat(node.getProperty("rx"), 0.0f);
            float ry = parseFloat(node.getProperty("ry"), 0.0f);
            float rz = parseFloat(node.getProperty("rz"), 0.0f);
            Quat qChild = Quat.fromEulerDeg(rx, ry, rz);
            Quat qNew = delta.mul(qChild).normalized();
            Vec3 euler = qNew.toEulerDeg();
            node.setProperty("rx", trimFloat((float) euler.x));
            node.setProperty("ry", trimFloat((float) euler.y));
            node.setProperty("rz", trimFloat((float) euler.z));
            changed = true;
        }

        return changed;
    }

    private boolean applyScaleDeltaToDescendants(Node node, Vec3 pivot, Quat parentRot, Vec3 factors) {
        boolean changed = false;
        for (Node child : node.children()) {
            if (child == null) {
                continue;
            }

            changed |= applyScaleDeltaToNode(child, pivot, parentRot, factors);
            changed |= applyScaleDeltaToDescendants(child, pivot, parentRot, factors);
        }
        return changed;
    }

    private boolean applyScaleDeltaToNode(Node node, Vec3 pivot, Quat parentRot, Vec3 factors) {
        boolean changed = false;

        if (nodeAcceptsKey(node, "x") && nodeAcceptsKey(node, "y") && nodeAcceptsKey(node, "z")) {
            NodePose pose = readPose(node);
            Vec3 offsetWorld = pose.pivot.sub(pivot);
            Vec3 offsetLocal = parentRot.rotateInverse(offsetWorld);
            Vec3 scaledLocal = new Vec3(offsetLocal.x * factors.x, offsetLocal.y * factors.y, offsetLocal.z * factors.z);
            Vec3 scaledWorld = parentRot.rotate(scaledLocal);
            Vec3 newPivot = pivot.add(scaledWorld);

            Vec3 newSize = pose.size;
            if (pose.hasSize && nodeAcceptsKey(node, "sx") && nodeAcceptsKey(node, "sy") && nodeAcceptsKey(node, "sz")) {
                long sx = Math.max(1L, Math.round(pose.size.x * factors.x));
                long sy = Math.max(1L, Math.round(pose.size.y * factors.y));
                long sz = Math.max(1L, Math.round(pose.size.z * factors.z));
                newSize = new Vec3(sx, sy, sz);
                node.setProperty("sx", Long.toString(sx));
                node.setProperty("sy", Long.toString(sy));
                node.setProperty("sz", Long.toString(sz));
                changed = true;
            }

            writePivot(node, newPivot, newSize);
            changed = true;
        }

        return changed;
    }

    private boolean nodeAcceptsKey(Node node, String key) {
        if (node == null || key == null || key.isBlank()) {
            return false;
        }
        String typeId = engine.nodeTypes().typeIdFor(node);
        NodeTypeDef typeDef = engine.nodeTypes().getType(typeId);
        if (typeDef != null && typeDef.properties() != null && typeDef.properties().containsKey(key)) {
            return true;
        }
        return node.getProperty(key) != null;
    }

    private NodePose readPose(Node node) {
        double x = parseFloat(node.getProperty("x"), 0.0f);
        double y = parseFloat(node.getProperty("y"), 0.0f);
        double z = parseFloat(node.getProperty("z"), 0.0f);

        boolean hasSize = nodeAcceptsKey(node, "sx") && nodeAcceptsKey(node, "sy") && nodeAcceptsKey(node, "sz");
        double sx = hasSize ? Math.max(1.0, parseFloat(node.getProperty("sx"), 1.0f)) : 0.0;
        double sy = hasSize ? Math.max(1.0, parseFloat(node.getProperty("sy"), 1.0f)) : 0.0;
        double sz = hasSize ? Math.max(1.0, parseFloat(node.getProperty("sz"), 1.0f)) : 0.0;

        Vec3 size = new Vec3(sx, sy, sz);
        Vec3 pivot = hasSize
                ? new Vec3(x + sx * 0.5, y + sy * 0.5, z + sz * 0.5)
                : new Vec3(x, y, z);

        return new NodePose(pivot, size, hasSize);
    }

    private Vec3 nodePivot(Node node) {
        if (node == null || !(nodeAcceptsKey(node, "x") && nodeAcceptsKey(node, "y") && nodeAcceptsKey(node, "z"))) {
            return new Vec3(0, 0, 0);
        }
        return readPose(node).pivot;
    }

    private void writePivot(Node node, Vec3 pivot, Vec3 size) {
        if (node == null || pivot == null || !(nodeAcceptsKey(node, "x") && nodeAcceptsKey(node, "y") && nodeAcceptsKey(node, "z"))) {
            return;
        }

        boolean hasSize = size != null
                && nodeAcceptsKey(node, "sx") && nodeAcceptsKey(node, "sy") && nodeAcceptsKey(node, "sz")
                && size.x > 0.0 && size.y > 0.0 && size.z > 0.0;

        double baseX = hasSize ? (pivot.x - size.x * 0.5) : pivot.x;
        double baseY = hasSize ? (pivot.y - size.y * 0.5) : pivot.y;
        double baseZ = hasSize ? (pivot.z - size.z * 0.5) : pivot.z;

        node.setProperty("x", trimFloat((float) baseX));
        node.setProperty("y", trimFloat((float) baseY));
        node.setProperty("z", trimFloat((float) baseZ));
    }

    private record NodePose(Vec3 pivot, Vec3 size, boolean hasSize) {
    }

    private record Vec3(double x, double y, double z) {
        Vec3 add(Vec3 o) {
            return new Vec3(x + o.x, y + o.y, z + o.z);
        }

        Vec3 sub(Vec3 o) {
            return new Vec3(x - o.x, y - o.y, z - o.z);
        }

        Vec3 mul(Vec3 o) {
            return new Vec3(x * o.x, y * o.y, z * o.z);
        }

        boolean isNear(Vec3 o, double eps) {
            return o != null && Math.abs(x - o.x) <= eps && Math.abs(y - o.y) <= eps && Math.abs(z - o.z) <= eps;
        }
    }

    private record Quat(double x, double y, double z, double w) {
        static final Quat IDENTITY = new Quat(0, 0, 0, 1);

        static Quat fromEulerDeg(float rxDeg, float ryDeg, float rzDeg) {
            double rx = Math.toRadians(Float.isFinite(rxDeg) ? rxDeg : 0.0f);
            double ry = Math.toRadians(Float.isFinite(ryDeg) ? ryDeg : 0.0f);
            double rz = Math.toRadians(Float.isFinite(rzDeg) ? rzDeg : 0.0f);

            double hx = rx * 0.5;
            double hy = ry * 0.5;
            double hz = rz * 0.5;

            double sx = Math.sin(hx), cx = Math.cos(hx);
            double sy = Math.sin(hy), cy = Math.cos(hy);
            double sz = Math.sin(hz), cz = Math.cos(hz);

            Quat qx = new Quat(sx, 0, 0, cx);
            Quat qy = new Quat(0, sy, 0, cy);
            Quat qz = new Quat(0, 0, sz, cz);

            return qz.mul(qy).mul(qx).normalized();
        }

        Quat mul(Quat b) {
            // Hamilton product: this * b
            double nx = w * b.x + x * b.w + y * b.z - z * b.y;
            double ny = w * b.y - x * b.z + y * b.w + z * b.x;
            double nz = w * b.z + x * b.y - y * b.x + z * b.w;
            double nw = w * b.w - x * b.x - y * b.y - z * b.z;
            return new Quat(nx, ny, nz, nw);
        }

        Quat inverse() {
            double n = x * x + y * y + z * z + w * w;
            if (n <= 0.0) {
                return IDENTITY;
            }
            double inv = 1.0 / n;
            return new Quat(-x * inv, -y * inv, -z * inv, w * inv);
        }

        Quat normalized() {
            double n = Math.sqrt(x * x + y * y + z * z + w * w);
            if (n <= 0.0) {
                return IDENTITY;
            }
            double inv = 1.0 / n;
            return new Quat(x * inv, y * inv, z * inv, w * inv);
        }

        boolean isIdentity(double eps) {
            return Math.abs(x) <= eps && Math.abs(y) <= eps && Math.abs(z) <= eps && Math.abs(w - 1.0) <= eps;
        }

        Vec3 rotate(Vec3 v) {
            if (v == null) {
                return new Vec3(0, 0, 0);
            }
            // q * (0,v) * q^-1 for unit quaternion.
            double vx = v.x, vy = v.y, vz = v.z;
            double tx = 2.0 * (y * vz - z * vy);
            double ty = 2.0 * (z * vx - x * vz);
            double tz = 2.0 * (x * vy - y * vx);
            return new Vec3(
                    vx + w * tx + (y * tz - z * ty),
                    vy + w * ty + (z * tx - x * tz),
                    vz + w * tz + (x * ty - y * tx)
            );
        }

        Vec3 rotateInverse(Vec3 v) {
            return inverse().normalized().rotate(v);
        }

        Vec3 toEulerDeg() {
            // Matches the matrix in CsgVoxelizer.OrientedBox.fromEulerAngles (Rz * Ry * Rx).
            Quat q = normalized();
            double xx = q.x * q.x;
            double yy = q.y * q.y;
            double zz = q.z * q.z;
            double xy = q.x * q.y;
            double xz = q.x * q.z;
            double yz = q.y * q.z;
            double wx = q.w * q.x;
            double wy = q.w * q.y;
            double wz = q.w * q.z;

            double m00 = 1.0 - 2.0 * (yy + zz);
            double m10 = 2.0 * (xy + wz);
            double m20 = 2.0 * (xz - wy);
            double m21 = 2.0 * (yz + wx);
            double m22 = 1.0 - 2.0 * (xx + yy);

            double sy = -m20;
            sy = Math.max(-1.0, Math.min(1.0, sy));
            double ry = Math.asin(sy);
            double cy = Math.cos(ry);

            double rx;
            double rz;
            if (Math.abs(cy) > 1e-12) {
                rx = Math.atan2(m21, m22);
                rz = Math.atan2(m10, m00);
            } else {
                // Gimbal lock: pick rx=0 and solve rz from remaining terms.
                rx = 0.0;
                double m01 = 2.0 * (xy - wz);
                double m11 = 1.0 - 2.0 * (xx + zz);
                rz = Math.atan2(-m01, m11);
            }

            return new Vec3(normalizeDeg(Math.toDegrees(rx)), normalizeDeg(Math.toDegrees(ry)), normalizeDeg(Math.toDegrees(rz)));
        }

        private static double normalizeDeg(double deg) {
            if (!Double.isFinite(deg)) {
                return 0.0;
            }
            double wrapped = deg % 360.0;
            if (wrapped > 180.0) {
                wrapped -= 360.0;
            } else if (wrapped < -180.0) {
                wrapped += 360.0;
            }
            if (Math.abs(wrapped) < 1e-6) {
                return 0.0;
            }
            return wrapped;
        }
    }

    private boolean isPrefabGenerated(Node node) {
        if (node == null) {
            return false;
        }
        return isTrue(node.getProperty(PROP_PREFAB_GENERATED));
    }

    private boolean isSceneInstance(Node node) {
        if (node == null) {
            return false;
        }
        return TYPE_SCENE_INSTANCE.equals(engine.nodeTypes().typeIdFor(node));
    }

    private static boolean isTrue(String v) {
        if (v == null) {
            return false;
        }
        String s = v.trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s);
    }

    private record ApplyOutcome(SceneOpResult result, boolean sceneChanged, boolean csgChanged,
                                boolean physicsChanged, boolean filterChanged) {
        private ApplyOutcome(SceneOpResult result, boolean sceneChanged) {
            this(result, sceneChanged, false, false, false);
        }

        private ApplyOutcome(SceneOpResult result, boolean sceneChanged, boolean csgChanged) {
            this(result, sceneChanged, csgChanged, false, false);
        }
    }

}
