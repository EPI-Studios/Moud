package com.moud.server.primitives;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.network.MoudPackets.*;
import com.moud.plugin.api.services.PrimitiveService;
import com.moud.plugin.api.services.primitives.PrimitiveHandle;
import com.moud.plugin.api.services.primitives.PrimitiveMaterial;
import com.moud.plugin.api.services.primitives.PrimitiveType;
import com.moud.server.instance.InstanceManager;
import com.moud.server.physics.PrimitivePhysicsManager;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class PrimitiveServiceImpl implements PrimitiveService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrimitiveServiceImpl.class);
    private static PrimitiveServiceImpl instance;
    private final Map<Long, PrimitiveInstance> primitives = new ConcurrentHashMap<>();
    private final Collection<PrimitiveInstance> primitiveInstancesView =
            Collections.unmodifiableCollection(primitives.values());
    private final Map<String, Set<Long>> groups = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);
    private final List<PrimitiveInstance> batchCreates = new ArrayList<>();
    private final List<PrimitiveInstance> batchTransforms = new ArrayList<>();
    private boolean batching = false;
    private PrimitivePacketSender packetSender;

    private PrimitiveServiceImpl() {
    }

    public static synchronized PrimitiveServiceImpl getInstance() {
        if (instance == null) {
            instance = new PrimitiveServiceImpl();
        }
        return instance;
    }

    public void setPacketSender(PrimitivePacketSender sender) {
        this.packetSender = sender;
    }

    public PrimitiveHandle createWithPhysics(
            PrimitiveType type,
            Vector3 position,
            Quaternion rotation,
            Vector3 scale,
            PrimitiveMaterial material,
            String groupId,
            boolean dynamic,
            float mass
    ) {
        long id = idCounter.getAndIncrement();
        PrimitiveInstance prim = new PrimitiveInstance(id, type, position, rotation, scale, material, groupId, this);
        prim.configurePhysics(dynamic, mass);
        LOGGER.info("Creating primitive {} type={} pos={} scale={} group={} dynamic={} mass={}",
                id, type, position, scale, groupId, dynamic, mass);
        primitives.put(id, prim);
        if (groupId != null) {
            groups.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet()).add(id);
        }
        if (batching) {
            batchCreates.add(prim);
        } else {
            broadcastCreate(prim);
        }
        PrimitivePhysicsManager.getInstance()
                .onCreate(prim, InstanceManager.getInstance().getDefaultInstance());
        return prim;
    }

    public PrimitiveHandle createMeshWithPhysics(
            Vector3 position,
            Quaternion rotation,
            Vector3 scale,
            List<Vector3> vertices,
            List<Integer> indices,
            PrimitiveMaterial material,
            String groupId,
            boolean dynamic,
            float mass
    ) {
        long id = idCounter.getAndIncrement();
        PrimitiveInstance prim = new PrimitiveInstance(
                id,
                PrimitiveType.MESH,
                position,
                rotation,
                scale,
                material,
                groupId,
                this,
                vertices,
                indices
        );
        prim.configurePhysics(dynamic, mass);
        LOGGER.info(
                "Creating primitive {} type={} pos={} scale={} group={} verts={} indices={} dynamic={} mass={}",
                id,
                PrimitiveType.MESH,
                prim.getPosition(),
                prim.getScale(),
                groupId,
                prim.getVertices().size(),
                prim.getIndices().size(),
                dynamic,
                mass
        );
        primitives.put(id, prim);
        if (groupId != null) {
            groups.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet()).add(id);
        }
        if (batching) {
            batchCreates.add(prim);
        } else {
            broadcastCreate(prim);
        }
        PrimitivePhysicsManager.getInstance()
                .onCreate(prim, InstanceManager.getInstance().getDefaultInstance());
        return prim;
    }

    public void applyPhysicsTransform(long primitiveId, Vector3 position, Quaternion rotation) {
        PrimitiveInstance prim = primitives.get(primitiveId);
        if (prim == null || prim.isRemoved()) {
            return;
        }
        prim.applyPhysicsTransform(position, rotation);
        if (packetSender == null) {
            return;
        }
        S2C_PrimitiveTransformPacket packet = new S2C_PrimitiveTransformPacket(
                prim.getId(),
                prim.getPosition(),
                prim.getRotation(),
                prim.getScale()
        );
        packetSender.broadcastToAll(packet);
    }

    @Override
    public PrimitiveHandle createCube(Vector3 position, Vector3 scale, PrimitiveMaterial material) {
        return create(PrimitiveType.CUBE, position, Quaternion.identity(), scale, material, null);
    }

    @Override
    public PrimitiveHandle createSphere(Vector3 position, float radius, PrimitiveMaterial material) {
        Vector3 scale = new Vector3(radius * 2, radius * 2, radius * 2);
        return create(PrimitiveType.SPHERE, position, Quaternion.identity(), scale, material, null);
    }

    @Override
    public PrimitiveHandle createCylinder(Vector3 position, float radius, float height, PrimitiveMaterial material) {
        Vector3 scale = new Vector3(radius * 2, height, radius * 2);
        return create(PrimitiveType.CYLINDER, position, Quaternion.identity(), scale, material, null);
    }

    @Override
    public PrimitiveHandle createCapsule(Vector3 position, float radius, float height, PrimitiveMaterial material) {
        Vector3 scale = new Vector3(radius * 2, height, radius * 2);
        return create(PrimitiveType.CAPSULE, position, Quaternion.identity(), scale, material, null);
    }

    @Override
    public PrimitiveHandle createCone(Vector3 position, float radius, float height, PrimitiveMaterial material) {
        Vector3 scale = new Vector3(radius * 2, height, radius * 2);
        return create(PrimitiveType.CONE, position, Quaternion.identity(), scale, material, null);
    }

    @Override
    public PrimitiveHandle createPlane(Vector3 position, float width, float depth, PrimitiveMaterial material) {
        Vector3 scale = new Vector3(width, 1, depth);
        PrimitiveMaterial mat = material != null ? material.copy() : PrimitiveMaterial.white();
        mat.setDoubleSided(true);
        return create(PrimitiveType.PLANE, position, Quaternion.identity(), scale, mat, null);
    }

    @Override
    public PrimitiveHandle createLine(Vector3 start, Vector3 end, PrimitiveMaterial material) {
        Vector3 midpoint = start.add(end).multiply(0.5f);
        PrimitiveInstance prim = (PrimitiveInstance) create(PrimitiveType.LINE, midpoint,
                Quaternion.identity(), Vector3.one(), material, null);
        List<Vector3> vertices = new ArrayList<>();
        vertices.add(start);
        vertices.add(end);
        prim.setVertices(vertices);
        return prim;
    }

    @Override
    public PrimitiveHandle createLineStrip(List<Vector3> points, PrimitiveMaterial material) {
        if (points == null || points.isEmpty()) {
            return create(PrimitiveType.LINE_STRIP, Vector3.zero(), Quaternion.identity(),
                    Vector3.one(), material, null);
        }
        Vector3 centroid = Vector3.zero();
        for (Vector3 p : points) {
            centroid = centroid.add(p);
        }
        centroid = centroid.multiply(1.0f / points.size());
        PrimitiveInstance prim = (PrimitiveInstance) create(PrimitiveType.LINE_STRIP, centroid,
                Quaternion.identity(), Vector3.one(), material, null);
        prim.setVertices(points);
        return prim;
    }

    @Override
    public PrimitiveHandle createBone(Vector3 from, Vector3 to, float thickness, PrimitiveMaterial material) {
        PrimitiveInstance prim = (PrimitiveInstance) create(PrimitiveType.CUBE, Vector3.zero(),
                Quaternion.identity(), Vector3.one(), material, null);
        prim.setFromTo(from, to, thickness);
        return prim;
    }

    @Override
    public PrimitiveHandle createMesh(Vector3 position, Quaternion rotation, Vector3 scale,
                                      List<Vector3> vertices, List<Integer> indices,
                                      PrimitiveMaterial material, String groupId) {
        long id = idCounter.getAndIncrement();
        PrimitiveInstance prim = new PrimitiveInstance(
                id,
                PrimitiveType.MESH,
                position,
                rotation,
                scale,
                material,
                groupId,
                this,
                vertices,
                indices
        );
        LOGGER.info("Creating primitive {} type={} pos={} scale={} group={} verts={} indices={}",
                id,
                PrimitiveType.MESH,
                prim.getPosition(),
                prim.getScale(),
                groupId,
                prim.getVertices().size(),
                prim.getIndices().size());
        primitives.put(id, prim);
        if (groupId != null) {
            groups.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet()).add(id);
        }
        if (batching) {
            batchCreates.add(prim);
        } else {
            broadcastCreate(prim);
        }
        PrimitivePhysicsManager.getInstance().onCreate(prim, InstanceManager.getInstance().getDefaultInstance());
        return prim;
    }

    @Override
    public PrimitiveHandle create(PrimitiveType type, Vector3 position, Quaternion rotation,
                                  Vector3 scale, PrimitiveMaterial material, String groupId) {
        long id = idCounter.getAndIncrement();
        PrimitiveInstance prim = new PrimitiveInstance(id, type, position, rotation, scale, material, groupId, this);
        LOGGER.info("Creating primitive {} type={} pos={} scale={} group={}",
                id, type, position, scale, groupId);
        primitives.put(id, prim);
        if (groupId != null) {
            groups.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet()).add(id);
        }
        if (batching) {
            batchCreates.add(prim);
        } else {
            broadcastCreate(prim);
        }
        PrimitivePhysicsManager.getInstance().onCreate(prim, InstanceManager.getInstance().getDefaultInstance());
        return prim;
    }

    @Override
    public PrimitiveHandle getPrimitive(long primitiveId) {
        return primitives.get(primitiveId);
    }

    @Override
    public Collection<PrimitiveHandle> getAllPrimitives() {
        return new ArrayList<>(primitives.values());
    }

    public Collection<PrimitiveInstance> getPrimitiveInstances() {
        return primitiveInstancesView;
    }

    @Override
    public Collection<PrimitiveHandle> getPrimitivesInGroup(String groupId) {
        Set<Long> ids = groups.get(groupId);
        if (ids == null) return Collections.emptyList();
        return ids.stream()
                .map(primitives::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public boolean removePrimitive(long primitiveId) {
        PrimitiveInstance prim = primitives.remove(primitiveId);
        if (prim != null) {
            if (prim.getGroupId() != null) {
                Set<Long> groupSet = groups.get(prim.getGroupId());
                if (groupSet != null) {
                    groupSet.remove(primitiveId);
                }
            }
            broadcastRemove(prim);
            com.moud.server.physics.PrimitivePhysicsManager.getInstance().onRemove(primitiveId);
            return true;
        }
        return false;
    }

    @Override
    public void removeGroup(String groupId) {
        Set<Long> ids = groups.remove(groupId);
        if (ids != null) {
            for (Long id : ids) {
                primitives.remove(id);
                com.moud.server.physics.PrimitivePhysicsManager.getInstance().onRemove(id);
            }
            broadcastRemoveGroup(groupId);
        }
    }

    @Override
    public void removeAll() {
        for (String groupId : new ArrayList<>(groups.keySet())) {
            removeGroup(groupId);
        }
        for (Long id : new ArrayList<>(primitives.keySet())) {
            removePrimitive(id);
        }
    }

    @Override
    public void beginBatch() {
        batching = true;
        batchCreates.clear();
        batchTransforms.clear();
    }

    @Override
    public void endBatch() {
        if (!batching) return;
        batching = false;
        if (!batchCreates.isEmpty()) {
            broadcastBatchCreate(batchCreates);
            batchCreates.clear();
        }
        if (!batchTransforms.isEmpty()) {
            broadcastBatchTransform(batchTransforms);
            for (PrimitiveInstance prim : batchTransforms) {
                PrimitivePhysicsManager.getInstance().onTransform(prim);
            }
            batchTransforms.clear();
        }
    }

    @Override
    public boolean isBatching() {
        return batching;
    }

    void removePrimitiveInternal(PrimitiveInstance prim) {
        primitives.remove(prim.getId());
        if (prim.getGroupId() != null) {
            Set<Long> groupSet = groups.get(prim.getGroupId());
            if (groupSet != null) {
                groupSet.remove(prim.getId());
            }
        }
        broadcastRemove(prim);
        PrimitivePhysicsManager.getInstance().onRemove(prim.getId());
    }

    void broadcastCreate(PrimitiveInstance prim) {
        if (packetSender == null) return;
        LOGGER.info(
                "Broadcasting primitive_create id={} type={} pos={} scale={} group={} " +
                        "verts={} indices={} dynamic={} mass={}",
                prim.getId(),
                prim.getType(),
                prim.getPosition(),
                prim.getScale(),
                prim.getGroupId(),
                prim.getVertices() != null ? prim.getVertices().size() : 0,
                prim.getIndices() != null ? prim.getIndices().size() : 0,
                prim.isPhysicsDynamic(),
                prim.getPhysicsMass()
        );
        PrimitiveMaterial mat = prim.getMaterial();
        com.moud.network.MoudPackets.PrimitiveMaterial packetMat = new com.moud.network.MoudPackets.PrimitiveMaterial(
                mat.r, mat.g, mat.b, mat.a, mat.texture, mat.unlit, mat.doubleSided, mat.renderThroughBlocks
        );
        com.moud.network.MoudPackets.PrimitiveType packetType = convertType(prim.getType());

        MoudPackets.PrimitivePhysics physics = buildPhysicsInfo(prim);

        S2C_PrimitiveCreatePacket packet = new S2C_PrimitiveCreatePacket(
                prim.getId(),
                packetType,
                prim.getPosition(),
                prim.getRotation(),
                prim.getScale(),
                packetMat,
                prim.getVertices().isEmpty() ? null : prim.getVertices(),
                prim.getGroupId(),
                prim.getIndices().isEmpty() ? null : prim.getIndices(),
                physics
        );
        packetSender.broadcastToAll(packet);
    }

    private MoudPackets.PrimitivePhysics buildPhysicsInfo(PrimitiveInstance prim) {
        // LINE and LINE_STRIP have no physics
        if (prim.getType() == PrimitiveType.LINE || prim.getType() == PrimitiveType.LINE_STRIP) {
            return MoudPackets.PrimitivePhysics.NONE;
        }
        // all other primitives have collision
        if (prim.isPhysicsDynamic()) {
            return MoudPackets.PrimitivePhysics.dynamic(prim.getPhysicsMass());
        }
        return MoudPackets.PrimitivePhysics.STATIC;
    }

    void broadcastTransform(PrimitiveInstance prim) {
        if (packetSender == null) return;
        if (batching) {
            batchTransforms.add(prim);
            return;
        }
        S2C_PrimitiveTransformPacket packet = new S2C_PrimitiveTransformPacket(
                prim.getId(),
                prim.getPosition(),
                prim.getRotation(),
                prim.getScale()
        );
        packetSender.broadcastToAll(packet);
        PrimitivePhysicsManager.getInstance().onTransform(prim);
    }

    void broadcastMaterial(PrimitiveInstance prim) {
        if (packetSender == null) return;
        PrimitiveMaterial mat = prim.getMaterial();
        MoudPackets.PrimitiveMaterial packetMat = new MoudPackets.PrimitiveMaterial(
                mat.r, mat.g, mat.b, mat.a, mat.texture, mat.unlit, mat.doubleSided, mat.renderThroughBlocks
        );
        S2C_PrimitiveMaterialPacket packet = new S2C_PrimitiveMaterialPacket(prim.getId(), packetMat);
        packetSender.broadcastToAll(packet);
    }

    void broadcastVertices(PrimitiveInstance prim) {
        if (packetSender == null) return;
        S2C_PrimitiveVerticesPacket packet = new S2C_PrimitiveVerticesPacket(
                prim.getId(),
                prim.getVertices(),
                prim.getIndices().isEmpty() ? null : prim.getIndices()
        );
        packetSender.broadcastToAll(packet);
        if (prim.getType() == PrimitiveType.MESH) {
            PrimitivePhysicsManager.getInstance()
                    .onGeometryChanged(prim, InstanceManager.getInstance().getDefaultInstance());
        }
    }

    void broadcastRemove(PrimitiveInstance prim) {
        if (packetSender == null) return;
        S2C_PrimitiveRemovePacket packet = new S2C_PrimitiveRemovePacket(prim.getId());
        packetSender.broadcastToAll(packet);
    }

    void broadcastRemoveGroup(String groupId) {
        if (packetSender == null) return;
        S2C_PrimitiveRemoveGroupPacket packet = new S2C_PrimitiveRemoveGroupPacket(groupId);
        packetSender.broadcastToAll(packet);
    }

    void broadcastBatchCreate(List<PrimitiveInstance> prims) {
        if (packetSender == null) return;
        List<PrimitiveBatchEntry> entries = new ArrayList<>();
        for (PrimitiveInstance prim : prims) {
            PrimitiveMaterial mat = prim.getMaterial();
            MoudPackets.PrimitiveMaterial packetMat = new MoudPackets.PrimitiveMaterial(
                    mat.r, mat.g, mat.b, mat.a, mat.texture, mat.unlit, mat.doubleSided, mat.renderThroughBlocks
            );
            entries.add(new PrimitiveBatchEntry(
                    prim.getId(),
                    convertType(prim.getType()),
                    prim.getPosition(),
                    prim.getRotation(),
                    prim.getScale(),
                    packetMat,
                    prim.getVertices().isEmpty() ? null : prim.getVertices(),
                    prim.getGroupId(),
                    prim.getIndices().isEmpty() ? null : prim.getIndices(),
                    buildPhysicsInfo(prim)
            ));
        }
        S2C_PrimitiveBatchCreatePacket packet = new S2C_PrimitiveBatchCreatePacket(entries);
        packetSender.broadcastToAll(packet);
    }

    void broadcastBatchTransform(List<PrimitiveInstance> prims) {
        if (packetSender == null) return;
        List<PrimitiveTransformEntry> entries = new ArrayList<>();
        for (PrimitiveInstance prim : prims) {
            entries.add(new PrimitiveTransformEntry(
                    prim.getId(),
                    prim.getPosition(),
                    prim.getRotation(),
                    prim.getScale()
            ));
            prim.clearDirty();
        }
        S2C_PrimitiveBatchTransformPacket packet = new S2C_PrimitiveBatchTransformPacket(entries);
        packetSender.broadcastToAll(packet);
    }

    private MoudPackets.PrimitiveType convertType(PrimitiveType type) {
        return switch (type) {
            case CUBE -> MoudPackets.PrimitiveType.CUBE;
            case SPHERE -> MoudPackets.PrimitiveType.SPHERE;
            case CYLINDER -> MoudPackets.PrimitiveType.CYLINDER;
            case CAPSULE -> MoudPackets.PrimitiveType.CAPSULE;
            case LINE -> MoudPackets.PrimitiveType.LINE;
            case LINE_STRIP -> MoudPackets.PrimitiveType.LINE_STRIP;
            case PLANE -> MoudPackets.PrimitiveType.PLANE;
            case CONE -> MoudPackets.PrimitiveType.CONE;
            case MESH -> MoudPackets.PrimitiveType.MESH;
        };
    }

    public void syncToPlayer(net.minestom.server.entity.Player player) {
        if (packetSender == null || player == null || primitives.isEmpty()) {
            return;
        }
        List<PrimitiveBatchEntry> entries = new ArrayList<>();
        for (PrimitiveInstance prim : primitives.values()) {
            PrimitiveMaterial mat = prim.getMaterial();
            MoudPackets.PrimitiveMaterial packetMat = new MoudPackets.PrimitiveMaterial(
                    mat.r, mat.g, mat.b, mat.a, mat.texture, mat.unlit, mat.doubleSided, mat.renderThroughBlocks
            );
            entries.add(new PrimitiveBatchEntry(
                    prim.getId(),
                    convertType(prim.getType()),
                    prim.getPosition(),
                    prim.getRotation(),
                    prim.getScale(),
                    packetMat,
                    prim.getVertices().isEmpty() ? null : prim.getVertices(),
                    prim.getGroupId(),
                    prim.getIndices().isEmpty() ? null : prim.getIndices(),
                    buildPhysicsInfo(prim)
            ));
        }
        if (!entries.isEmpty()) {
            S2C_PrimitiveBatchCreatePacket packet = new S2C_PrimitiveBatchCreatePacket(entries);
            packetSender.sendToPlayer(player, packet);
        }
    }

    public interface PrimitivePacketSender {
        void broadcastToAll(Object packet);

        void sendToPlayer(Player player, Object packet);
    }
}
