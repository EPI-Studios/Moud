package com.moud.client.primitives;

import com.moud.network.MoudPackets;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientPrimitiveManager {
    private static final ClientPrimitiveManager INSTANCE = new ClientPrimitiveManager();

    private final Map<Long, ClientPrimitive> primitives = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> groups = new ConcurrentHashMap<>();

    private ClientPrimitiveManager() {
    }

    public static ClientPrimitiveManager getInstance() {
        return INSTANCE;
    }

    public void handleCreate(MoudPackets.S2C_PrimitiveCreatePacket packet) {
        if (packet == null) return;
        ClientPrimitive primitive = new ClientPrimitive(
                packet.primitiveId(),
                packet.type(),
                packet.position(),
                packet.rotation(),
                packet.scale(),
                packet.material(),
                packet.vertices(),
                packet.indices(),
                packet.groupId(),
                packet.physics()
        );
        primitives.put(packet.primitiveId(), primitive);
        if (packet.groupId() != null) {
            groups.computeIfAbsent(packet.groupId(), key -> ConcurrentHashMap.newKeySet())
                    .add(packet.primitiveId());
        }

        if (primitive.hasCollision()) {
            PrimitiveCollisionManager.getInstance().registerPrimitive(primitive);
            PrimitiveMeshCollisionManager.getInstance().registerPrimitive(primitive);
        }

        com.moud.client.MoudClientMod.LOGGER.info("[Primitives] Created id={} type={} group={} verts={} collision={} dynamic={}",
                packet.primitiveId(), packet.type(), packet.groupId(),
                packet.vertices() != null ? packet.vertices().size() : 0,
                primitive.hasCollision(), primitive.isDynamic());
    }

    public void handleBatchCreate(MoudPackets.S2C_PrimitiveBatchCreatePacket packet) {
        if (packet == null || packet.primitives() == null) {
            return;
        }
        for (MoudPackets.PrimitiveBatchEntry entry : packet.primitives()) {
            handleCreate(new MoudPackets.S2C_PrimitiveCreatePacket(
                    entry.primitiveId(),
                    entry.type(),
                    entry.position(),
                    entry.rotation(),
                    entry.scale(),
                    entry.material(),
                    entry.vertices(),
                    entry.groupId(),
                    entry.indices(),
                    entry.physics()
            ));
        }
    }

    public void handleTransform(MoudPackets.S2C_PrimitiveTransformPacket packet) {
        if (packet == null) return;
        ClientPrimitive primitive = primitives.get(packet.primitiveId());
        if (primitive != null) {
            primitive.updateTransform(packet.position(), packet.rotation(), packet.scale());
            if (primitive.hasCollision()) {
                PrimitiveCollisionManager.getInstance().updatePrimitive(primitive);
                PrimitiveMeshCollisionManager.getInstance().updatePrimitive(primitive);
            }
        } else {
            com.moud.client.MoudClientMod.LOGGER.warn("[Primitives] Transform for unknown id {}", packet.primitiveId());
        }
    }

    public void handleBatchTransform(MoudPackets.S2C_PrimitiveBatchTransformPacket packet) {
        if (packet == null || packet.transforms() == null) return;
        for (MoudPackets.PrimitiveTransformEntry entry : packet.transforms()) {
            ClientPrimitive primitive = primitives.get(entry.primitiveId());
            if (primitive != null) {
                primitive.updateTransform(entry.position(), entry.rotation(), entry.scale());
                if (primitive.hasCollision()) {
                    PrimitiveCollisionManager.getInstance().updatePrimitive(primitive);
                    PrimitiveMeshCollisionManager.getInstance().updatePrimitive(primitive);
                }
            }
        }
    }

    public void handleMaterial(MoudPackets.S2C_PrimitiveMaterialPacket packet) {
        if (packet == null) return;
        ClientPrimitive primitive = primitives.get(packet.primitiveId());
        if (primitive != null) {
            primitive.updateMaterial(packet.material());
        } else {
            com.moud.client.MoudClientMod.LOGGER.warn("[Primitives] Material for unknown id {}", packet.primitiveId());
        }
    }

    public void handleVertices(MoudPackets.S2C_PrimitiveVerticesPacket packet) {
        if (packet == null) return;
        ClientPrimitive primitive = primitives.get(packet.primitiveId());
        if (primitive != null) {
            primitive.updateMesh(packet.vertices(), packet.indices());
            if (primitive.hasCollision()) {
                PrimitiveCollisionManager.getInstance().updatePrimitive(primitive);
                PrimitiveMeshCollisionManager.getInstance().updatePrimitive(primitive);
            }
        } else {
            com.moud.client.MoudClientMod.LOGGER.warn("[Primitives] Vertices for unknown id {}", packet.primitiveId());
        }
    }

    public void handleRemove(MoudPackets.S2C_PrimitiveRemovePacket packet) {
        if (packet == null) return;
        remove(packet.primitiveId());
    }

    public void handleRemoveGroup(MoudPackets.S2C_PrimitiveRemoveGroupPacket packet) {
        if (packet == null || packet.groupId() == null) return;
        Set<Long> ids = groups.remove(packet.groupId());
        if (ids != null) {
            for (Long id : ids) {
                remove(id);
            }
        }
    }

    private void remove(long id) {
        ClientPrimitive removed = primitives.remove(id);
        if (removed != null) {
            if (removed.getGroupId() != null) {
                Set<Long> set = groups.get(removed.getGroupId());
                if (set != null) {
                    set.remove(id);
                }
            }
            if (removed.hasCollision()) {
                PrimitiveCollisionManager.getInstance().unregisterPrimitive(id);
                PrimitiveMeshCollisionManager.getInstance().unregisterPrimitive(id);
            }
        }
    }

    public Collection<ClientPrimitive> getPrimitives() {
        return Collections.unmodifiableCollection(primitives.values());
    }

    public boolean isEmpty() {
        return primitives.isEmpty();
    }

    public void tickSmoothing(float deltaTicks) {
        for (ClientPrimitive primitive : primitives.values()) {
            primitive.tickSmoothing(deltaTicks);
        }
    }

    public void clear() {
        // Clear all collisions
        PrimitiveCollisionManager.getInstance().clear();
        PrimitiveMeshCollisionManager.getInstance().clear();
        primitives.clear();
        groups.clear();
    }
}
