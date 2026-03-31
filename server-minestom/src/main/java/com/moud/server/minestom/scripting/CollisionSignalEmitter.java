package com.moud.server.minestom.scripting;

import com.moud.core.physics.BodyHandle;
import com.moud.core.scene.Node;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.physics.CollisionEvent;
import com.moud.server.minestom.physics.JoltPhysicsWorld;
import com.moud.server.minestom.physics.CollisionLayerMask;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

import java.util.*;
import java.util.function.Supplier;

final class CollisionSignalEmitter {

    private final HashSet<Long> previousPairs = new HashSet<>();
    private final HashSet<Long> currentPairs = new HashSet<>();

    HashSet<Long> previousPairs() { return previousPairs; }
    HashSet<Long> currentPairs()  { return currentPairs; }

    void emit(ServerScene scene, Map<Long, SceneRuntime.NodeInstance> instances,
              SignalBus signalBus, Supplier<Map<Long, ScriptObject>> valueMapSupplier) {
        if (scene == null) return;
        JoltPhysicsWorld physics = scene.physics();
        Map<Long, ScriptObject> valueMap = null;

        currentPairs.clear();

        if (physics != null) {
            List<CollisionEvent> events = physics.consumeCollisionEvents();
            for (CollisionEvent ev : events) {
                long pairKey = pairKey(ev.nodeIdA(), ev.nodeIdB());
                currentPairs.add(pairKey);
                if (!previousPairs.contains(pairKey)) {
                    if (valueMap == null) valueMap = valueMapSupplier.get();
                    emitBodyEntered(scene, instances, signalBus, ev.nodeIdA(), ev.nodeIdB(),
                            ev.contactX(), ev.contactY(), ev.contactZ(), valueMap);
                    emitBodyEntered(scene, instances, signalBus, ev.nodeIdB(), ev.nodeIdA(),
                            ev.contactX(), ev.contactY(), ev.contactZ(), valueMap);
                }
            }

            Set<Long> joltActive = physics.currentContactPairs();
            for (long pair : joltActive) {
                currentPairs.add(pair);
            }
        }

        for (SceneRuntime.NodeInstance inst : instances.values()) {
            if (inst == null || inst.disabled) continue;
            Node node = scene.engine().sceneTree().getNode(inst.nodeId);
            if (node == null) continue;
            String typeId = scene.engine().nodeTypes().typeIdFor(node);
            if (!isAreaNode(typeId)) continue;
            if (!parseBoolSafe(node.getProperty("monitoring"), true)) continue;

            float x = parseFloatSafe(node.getProperty("x"), 0f);
            float y = parseFloatSafe(node.getProperty("y"), 0f);
            float z = parseFloatSafe(node.getProperty("z"), 0f);

            if (physics != null) {
                int areaLayer = CollisionLayerMask.layer(node);
                int areaMask = CollisionLayerMask.mask(node);
                String shape = node.getProperty("shape");
                float radius = 1.0f;
                if ("sphere".equalsIgnoreCase(shape)) {
                    radius = Math.max(0.01f, parseFloatSafe(node.getProperty("radius"), 1.0f));
                } else if ("box".equalsIgnoreCase(shape)) {
                    float sx = Math.max(0.01f, parseFloatSafe(node.getProperty("sx"), 1.0f));
                    float sy = Math.max(0.01f, parseFloatSafe(node.getProperty("sy"), 1.0f));
                    float sz = Math.max(0.01f, parseFloatSafe(node.getProperty("sz"), 1.0f));
                    radius = 0.5f * Math.max(sx, Math.max(sy, sz));
                }

                List<BodyHandle> overlaps = physics.overlapSphere(x, y, z, radius);
                for (BodyHandle bh : overlaps) {
                    Long otherNodeId = physics.nodeIdForBody(bh.id());
                    if (otherNodeId == null || otherNodeId == inst.nodeId) continue;
                    Node other = scene.engine().sceneTree().getNode(otherNodeId);
                    if (other != null) {
                        int otherLayer = CollisionLayerMask.layer(other);
                        int otherMask = CollisionLayerMask.mask(other);
                        if ((areaLayer & otherMask) == 0 || (otherLayer & areaMask) == 0) {
                            continue;
                        }
                    }
                    long pairKey = pairKey(inst.nodeId, otherNodeId);
                    currentPairs.add(pairKey);
                    if (!previousPairs.contains(pairKey)) {
                        if (valueMap == null) valueMap = valueMapSupplier.get();
                        signalBus.emit(inst.nodeId, "area_entered", valueMap, otherNodeId);
                    }
                }
            }

            String shape = node.getProperty("shape");
            float radius = Math.max(0.01f, parseFloatSafe(node.getProperty("radius"), 1.0f));
            float halfX = Math.max(0.01f, parseFloatSafe(node.getProperty("sx"), 1.0f)) * 0.5f;
            float halfY = Math.max(0.01f, parseFloatSafe(node.getProperty("sy"), 1.0f)) * 0.5f;
            float halfZ = Math.max(0.01f, parseFloatSafe(node.getProperty("sz"), 1.0f)) * 0.5f;
            boolean sphere = "sphere".equalsIgnoreCase(shape) || shape == null || shape.isBlank();

            if (scene.instance() != null) for (Player player : scene.instance().getPlayers()) {
                Pos p = player.getPosition();
                float px = (float) p.x();
                float py = (float) (p.y() + 0.9);
                float pz = (float) p.z();

                boolean inside;
                if (sphere) {
                    float dx = px - x;
                    float dy = py - y;
                    float dz = pz - z;
                    inside = (dx * dx + dy * dy + dz * dz) <= (radius * radius);
                } else if ("box".equalsIgnoreCase(shape)) {
                    inside = Math.abs(px - x) <= halfX
                            && Math.abs(py - y) <= halfY
                            && Math.abs(pz - z) <= halfZ;
                } else {
                    inside = false;
                }
                if (!inside) continue;

                long pairKey = pairKey(inst.nodeId, player.getEntityId());
                currentPairs.add(pairKey);
                if (!previousPairs.contains(pairKey)) {
                    if (valueMap == null) valueMap = valueMapSupplier.get();
                    signalBus.emit(inst.nodeId, "area_entered", valueMap, player.getUuid().toString());
                }
            }
        }

        if (valueMap == null) valueMap = valueMapSupplier.get();
        for (long pair : previousPairs) {
            if (!currentPairs.contains(pair)) {
                long nodeA = pairNodeId(pair);
                long nodeB = pairBodyId(pair);

                Node node = scene.engine().sceneTree().getNode(nodeA);
                if (node != null && isAreaNode(scene.engine().nodeTypes().typeIdFor(node))) {
                    Player player = findPlayerByEntityId(scene, (int) nodeB);
                    if (player != null) {
                        signalBus.emit(nodeA, "area_exited", valueMap, player.getUuid().toString());
                        continue;
                    }
                }

                emitBodyExited(scene, instances, signalBus, nodeA, nodeB, valueMap);
                emitBodyExited(scene, instances, signalBus, nodeB, nodeA, valueMap);
            }
        }

        previousPairs.clear();
        previousPairs.addAll(currentPairs);
    }

    private void emitBodyEntered(ServerScene scene, Map<Long, SceneRuntime.NodeInstance> instances,
                                 SignalBus signalBus, long nodeId, long otherNodeId,
                                 float cx, float cy, float cz, Map<Long, ScriptObject> valueMap) {
        SceneRuntime.NodeInstance inst = instances.get(nodeId);
        if (inst == null || inst.disabled) return;
        Node node = scene.engine().sceneTree().getNode(nodeId);
        if (node == null) return;
        String typeId = scene.engine().nodeTypes().typeIdFor(node);
        String signal = isAreaNode(typeId) ? "area_entered" : "body_entered";
        signalBus.emit(nodeId, signal, valueMap, otherNodeId);
        if (inst.instance.hasMethod("_on_body_entered")) {
            try {
                inst.instance.invokeMethod("_on_body_entered", inst.api, otherNodeId, cx, cy, cz);
            } catch (ScriptInvocationException e) {
                inst.disabled = true;
            }
        }
    }

    private void emitBodyExited(ServerScene scene, Map<Long, SceneRuntime.NodeInstance> instances,
                                SignalBus signalBus, long nodeId, long otherNodeId, Map<Long, ScriptObject> valueMap) {
        SceneRuntime.NodeInstance inst = instances.get(nodeId);
        if (inst == null || inst.disabled) return;
        Node node = scene.engine().sceneTree().getNode(nodeId);
        if (node == null) return;
        String typeId = scene.engine().nodeTypes().typeIdFor(node);
        String signal = isAreaNode(typeId) ? "area_exited" : "body_exited";
        signalBus.emit(nodeId, signal, valueMap, otherNodeId);
        if (inst.instance.hasMethod("_on_body_exited")) {
            try {
                inst.instance.invokeMethod("_on_body_exited", inst.api, otherNodeId);
            } catch (ScriptInvocationException e) {
                inst.disabled = true;
            }
        }
    }

    private static boolean isAreaNode(String typeId) {
        return "Area3D".equals(typeId);
    }

    static long pairKey(long nodeId, int bodyId) {
        return (nodeId << 32) | (bodyId & 0xFFFFFFFFL);
    }

    static long pairKey(long nodeIdA, long nodeIdB) {
        long lo = Math.min(nodeIdA, nodeIdB);
        long hi = Math.max(nodeIdA, nodeIdB);
        return (lo << 32) | (hi & 0xFFFFFFFFL);
    }

    private static long pairNodeId(long pairKey) {
        return pairKey >>> 32;
    }

    private static int pairBodyId(long pairKey) {
        return (int) pairKey;
    }

    private static float parseFloatSafe(String v, float fallback) {
        if (v == null || v.isBlank()) return fallback;
        try {
            float f = Float.parseFloat(v.trim());
            return Float.isFinite(f) ? f : fallback;
        } catch (Exception ignored) { return fallback; }
    }

    private static boolean parseBoolSafe(String v, boolean fallback) {
        if (v == null || v.isBlank()) return fallback;
        String s = v.trim();
        if ("1".equals(s)) return true;
        if ("0".equals(s)) return false;
        if ("true".equalsIgnoreCase(s)) return true;
        if ("false".equalsIgnoreCase(s)) return false;
        return fallback;
    }

    private static Player findPlayerByEntityId(ServerScene scene, int entityId) {
        if (scene == null || scene.instance() == null) return null;
        for (Player p : scene.instance().getPlayers()) {
            if (p != null && p.getEntityId() == entityId) return p;
        }
        return null;
    }
}
