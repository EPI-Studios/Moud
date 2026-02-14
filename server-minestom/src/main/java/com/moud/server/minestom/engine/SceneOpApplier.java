package com.moud.server.minestom.engine;

import com.moud.core.scene.Node;
import com.moud.core.scene.PlainNode;
import com.moud.core.NodeTypeDef;
import com.moud.net.protocol.*;

import java.util.*;
import java.util.function.Consumer;

public final class SceneOpApplier {
    private static final Set<String> CSG_KEYS = Set.of("x", "y", "z", "rx", "ry", "rz", "sx", "sy", "sz", "block", "@type");
    private static final Set<String> POSITION_KEYS = Set.of("x", "y", "z");
    private static final Set<String> ROTATION_KEYS = Set.of("rx", "ry", "rz");
    private static final Set<String> SCALE_KEYS = Set.of("sx", "sy", "sz");
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

        List<SceneOp> ops = batch.ops();
        for (int i = 0; i < ops.size(); i++) {
            SceneOp op = ops.get(i);

            if (op instanceof SceneOp.SetProperty setProperty && isTransformKey(setProperty.key())) {
                Node node = engine.sceneTree().getNode(setProperty.nodeId());
                if (node != null && node.children() != null && !node.children().isEmpty()) {
                    int j = i;
                    ArrayList<SceneOp.SetProperty> group = new ArrayList<>();
                    while (j < ops.size()) {
                        SceneOp next = ops.get(j);
                        if (!(next instanceof SceneOp.SetProperty sp)) {
                            break;
                        }
                        if (sp.nodeId() != setProperty.nodeId()) {
                            break;
                        }
                        if (!isTransformKey(sp.key())) {
                            break;
                        }
                        group.add(sp);
                        j++;
                    }

                    if (group.size() > 1 && canApplyTransformGroup(node, group)) {
                        ApplyOutcome out = applyTransformGroup(node, group);
                        for (int k = 0; k < group.size(); k++) {
                            results.add(out.result);
                        }
                        anySceneChanged |= out.sceneChanged;
                        anyCsgChanged |= out.csgChanged;
                        i = j - 1;
                        continue;
                    }
                }
            }

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

    private boolean isTransformKey(String key) {
        return POSITION_KEYS.contains(key) || ROTATION_KEYS.contains(key) || SCALE_KEYS.contains(key);
    }

    private boolean canApplyTransformGroup(Node node, List<SceneOp.SetProperty> group) {
        if (node == null || group == null || group.isEmpty()) {
            return false;
        }
        String typeId = engine.nodeTypes().typeIdFor(node);
        for (SceneOp.SetProperty op : group) {
            if (op == null || op.key() == null || op.key().isBlank()) {
                return false;
            }
            var vr = engine.nodeTypes().validateSetProperty(typeId, op.key(), op.value());
            if (!vr.ok()) {
                return false;
            }
        }
        return true;
    }

    private ApplyOutcome applyTransformGroup(Node node, List<SceneOp.SetProperty> group) {
        InheritedTransform before = readInheritedTransform(node, null);
        Map<String, String> overrides = new HashMap<>();
        for (SceneOp.SetProperty op : group) {
            overrides.put(op.key(), op.value());
        }
        InheritedTransform after = readInheritedTransform(node, overrides);

        boolean movedAny = false;
        if (!before.isNear(after, 1e-9)) {
            movedAny = applyTransformDeltaToDescendants(node, before, after);
        }

        boolean affectsCsg = false;
        for (SceneOp.SetProperty op : group) {
            node.setProperty(op.key(), op.value());
            affectsCsg |= affectsCsg(node, op.key());
        }
        if (movedAny) {
            affectsCsg |= subtreeContainsCsg(node);
        }

        return new ApplyOutcome(SceneOpResult.ok(node.nodeId()), true, affectsCsg);
    }

    private InheritedTransform readInheritedTransform(Node node, Map<String, String> overrides) {
        if (node == null) {
            return InheritedTransform.identity();
        }

        double x = parseFloat(overrideOr(node, overrides, "x"), 0.0f);
        double y = parseFloat(overrideOr(node, overrides, "y"), 0.0f);
        double z = parseFloat(overrideOr(node, overrides, "z"), 0.0f);

        float rx = parseFloat(overrideOr(node, overrides, "rx"), 0.0f);
        float ry = parseFloat(overrideOr(node, overrides, "ry"), 0.0f);
        float rz = parseFloat(overrideOr(node, overrides, "rz"), 0.0f);
        Quat rot = Quat.fromEulerDeg(rx, ry, rz);

        boolean hasScale = nodeAcceptsKey(node, "sx") && nodeAcceptsKey(node, "sy") && nodeAcceptsKey(node, "sz");
        double sx = hasScale ? Math.max(1e-6, parseFloat(overrideOr(node, overrides, "sx"), 1.0f)) : 1.0;
        double sy = hasScale ? Math.max(1e-6, parseFloat(overrideOr(node, overrides, "sy"), 1.0f)) : 1.0;
        double sz = hasScale ? Math.max(1e-6, parseFloat(overrideOr(node, overrides, "sz"), 1.0f)) : 1.0;
        Vec3 scale = new Vec3(sx, sy, sz);

        Vec3 pivot = hasScale
                ? new Vec3(x + sx * 0.5, y + sy * 0.5, z + sz * 0.5)
                : new Vec3(x, y, z);

        return new InheritedTransform(pivot, rot, scale, hasScale);
    }

    private static String overrideOr(Node node, Map<String, String> overrides, String key) {
        if (overrides != null) {
            String v = overrides.get(key);
            if (v != null) {
                return v;
            }
        }
        return node.getProperty(key);
    }

    private boolean applyTransformDeltaToDescendants(Node node, InheritedTransform before, InheritedTransform after) {
        if (node == null || before == null || after == null) {
            return false;
        }

        Quat qBefore = before.rot.normalized();
        Quat qAfter = after.rot.normalized();
        Quat qBeforeInv = qBefore.inverse().normalized();
        Quat qDeltaRot = qAfter.mul(qBeforeInv).normalized();

        Vec3 factors = new Vec3(
                safeDiv(after.scale.x, before.scale.x),
                safeDiv(after.scale.y, before.scale.y),
                safeDiv(after.scale.z, before.scale.z)
        );

        boolean scaleChanged = !factors.isNear(new Vec3(1.0, 1.0, 1.0), 1e-12);
        boolean rotChanged = !qDeltaRot.isIdentity(1e-12);

        return applyTransformDeltaRecursive(node, before, after, qBeforeInv, qAfter, qDeltaRot, factors, scaleChanged, rotChanged);
    }

    private boolean applyTransformDeltaRecursive(Node node,
                                                 InheritedTransform before,
                                                 InheritedTransform after,
                                                 Quat beforeInv,
                                                 Quat afterRot,
                                                 Quat deltaRot,
                                                 Vec3 factors,
                                                 boolean scaleChanged,
                                                 boolean rotChanged) {
        boolean changed = false;
        for (Node child : node.children()) {
            if (child == null) {
                continue;
            }
            changed |= applyTransformDeltaToNode(child, before, after, beforeInv, afterRot, deltaRot, factors, scaleChanged, rotChanged);
            changed |= applyTransformDeltaRecursive(child, before, after, beforeInv, afterRot, deltaRot, factors, scaleChanged, rotChanged);
        }
        return changed;
    }

    private boolean applyTransformDeltaToNode(Node node,
                                              InheritedTransform before,
                                              InheritedTransform after,
                                              Quat beforeInv,
                                              Quat afterRot,
                                              Quat deltaRot,
                                              Vec3 factors,
                                              boolean scaleChanged,
                                              boolean rotChanged) {
        boolean changed = false;

        if (nodeAcceptsKey(node, "x") && nodeAcceptsKey(node, "y") && nodeAcceptsKey(node, "z")) {
            NodePose pose = readPose(node);
            Vec3 offsetWorld = pose.pivot.sub(before.pivot);
            Vec3 offsetLocal = beforeInv.rotate(offsetWorld);
            Vec3 offsetScaled = offsetLocal.mul(factors);
            Vec3 offsetWorldAfter = afterRot.rotate(offsetScaled);
            Vec3 newPivot = after.pivot.add(offsetWorldAfter);

            Vec3 newSize = pose.size;
            if (scaleChanged && pose.hasSize && nodeAcceptsKey(node, "sx") && nodeAcceptsKey(node, "sy") && nodeAcceptsKey(node, "sz")) {
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

        if (rotChanged && nodeAcceptsKey(node, "rx") && nodeAcceptsKey(node, "ry") && nodeAcceptsKey(node, "rz")) {
            float rx = parseFloat(node.getProperty("rx"), 0.0f);
            float ry = parseFloat(node.getProperty("ry"), 0.0f);
            float rz = parseFloat(node.getProperty("rz"), 0.0f);
            Quat qChild = Quat.fromEulerDeg(rx, ry, rz);
            Quat qNew = deltaRot.mul(qChild).normalized();
            Vec3 euler = qNew.toEulerDeg();
            node.setProperty("rx", trimFloat((float) euler.x));
            node.setProperty("ry", trimFloat((float) euler.y));
            node.setProperty("rz", trimFloat((float) euler.z));
            changed = true;
        }

        return changed;
    }

    private static double safeDiv(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b) || Math.abs(b) < 1e-12) {
            return 1.0;
        }
        double v = a / b;
        return Double.isFinite(v) ? v : 1.0;
    }

    private record InheritedTransform(Vec3 pivot, Quat rot, Vec3 scale, boolean hasScale) {
        static InheritedTransform identity() {
            return new InheritedTransform(new Vec3(0, 0, 0), Quat.IDENTITY, new Vec3(1, 1, 1), false);
        }

        boolean isNear(InheritedTransform other, double eps) {
            if (other == null) {
                return false;
            }
            if (!pivot.isNear(other.pivot, eps)) {
                return false;
            }
            if (!scale.isNear(other.scale, eps)) {
                return false;
            }
            Quat delta = other.rot.mul(rot.inverse()).normalized();
            return delta.isIdentity(eps);
        }
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
                String key = setProperty.key();
                String value = setProperty.value();

                boolean affectsCsg = affectsCsg(node, key);

                if (POSITION_KEYS.contains(key) && node.children() != null && !node.children().isEmpty()) {
                    float before = parseFloat(node.getProperty(key), 0.0f);
                    float after = parseFloat(value, before);
                    float delta = after - before;
                    if (Math.abs(delta) > 1e-6f) {
                        boolean movedAny = applyPositionDeltaToDescendants(node, key, delta);
                        if (movedAny) {
                            affectsCsg |= subtreeContainsCsg(node);
                        }
                    }
                } else if (ROTATION_KEYS.contains(key) && node.children() != null && !node.children().isEmpty()) {
                    float beforeRx = parseFloat(node.getProperty("rx"), 0.0f);
                    float beforeRy = parseFloat(node.getProperty("ry"), 0.0f);
                    float beforeRz = parseFloat(node.getProperty("rz"), 0.0f);
                    float afterRx = "rx".equals(key) ? parseFloat(value, beforeRx) : beforeRx;
                    float afterRy = "ry".equals(key) ? parseFloat(value, beforeRy) : beforeRy;
                    float afterRz = "rz".equals(key) ? parseFloat(value, beforeRz) : beforeRz;

                    Quat qBefore = Quat.fromEulerDeg(beforeRx, beforeRy, beforeRz);
                    Quat qAfter = Quat.fromEulerDeg(afterRx, afterRy, afterRz);
                    Quat qDelta = qAfter.mul(qBefore.inverse()).normalized();

                    if (!qDelta.isIdentity(1e-9)) {
                        Vec3 pivot = nodePivot(node);
                        boolean movedAny = applyRotationDeltaToDescendants(node, pivot, qDelta);
                        if (movedAny) {
                            affectsCsg |= subtreeContainsCsg(node);
                        }
                    }
                } else if (SCALE_KEYS.contains(key) && node.children() != null && !node.children().isEmpty()) {
                    double beforeSx = Math.max(1e-6, parseFloat(node.getProperty("sx"), 1.0f));
                    double beforeSy = Math.max(1e-6, parseFloat(node.getProperty("sy"), 1.0f));
                    double beforeSz = Math.max(1e-6, parseFloat(node.getProperty("sz"), 1.0f));
                    double afterSx = "sx".equals(key) ? Math.max(1e-6, parseFloat(value, (float) beforeSx)) : beforeSx;
                    double afterSy = "sy".equals(key) ? Math.max(1e-6, parseFloat(value, (float) beforeSy)) : beforeSy;
                    double afterSz = "sz".equals(key) ? Math.max(1e-6, parseFloat(value, (float) beforeSz)) : beforeSz;

                    Vec3 factors = new Vec3(afterSx / beforeSx, afterSy / beforeSy, afterSz / beforeSz);
                    if (!factors.isNear(new Vec3(1.0, 1.0, 1.0), 1e-9)) {
                        float rx = parseFloat(node.getProperty("rx"), 0.0f);
                        float ry = parseFloat(node.getProperty("ry"), 0.0f);
                        float rz = parseFloat(node.getProperty("rz"), 0.0f);
                        Quat parentRot = Quat.fromEulerDeg(rx, ry, rz);
                        Vec3 pivot = nodePivot(node);

                        boolean movedAny = applyScaleDeltaToDescendants(node, pivot, parentRot, factors);
                        if (movedAny) {
                            affectsCsg |= subtreeContainsCsg(node);
                        }
                    }
                }

                node.setProperty(key, value);
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

    private record ApplyOutcome(SceneOpResult result, boolean sceneChanged, boolean csgChanged) {
        private ApplyOutcome(SceneOpResult result, boolean sceneChanged) {
            this(result, sceneChanged, false);
        }
    }

}
