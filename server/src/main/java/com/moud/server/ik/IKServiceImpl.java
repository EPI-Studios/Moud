package com.moud.server.ik;

import com.moud.api.ik.IKChainDefinition;
import com.moud.api.ik.IKChainState;
import com.moud.api.ik.IKSolver;
import com.moud.api.ik.IKSolverFactory;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets.*;
import com.moud.plugin.api.services.IKService;
import com.moud.plugin.api.services.ik.IKHandle;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class IKServiceImpl implements IKService {
    private static final Logger LOGGER = LoggerFactory.getLogger(IKServiceImpl.class);
    private static IKServiceImpl instance;
    private final Map<String, IKChainInstance> chains = new ConcurrentHashMap<>();
    private final AtomicLong tickCounter = new AtomicLong(0);
    private int defaultBroadcastRate = 1;
    private IKPacketSender packetSender;

    private IKServiceImpl() {
        MinecraftServer.getSchedulerManager().scheduleTask(() -> {
            tick();
        }, TaskSchedule.tick(1), TaskSchedule.tick(1));
    }

    public static synchronized IKServiceImpl getInstance() {
        if (instance == null) {
            instance = new IKServiceImpl();
        }
        return instance;
    }

    public void setPacketSender(IKPacketSender sender) {
        this.packetSender = sender;
    }

    private void tick() {
        long currentTick = tickCounter.incrementAndGet();
        for (IKChainInstance chain : chains.values()) {
            if (!chain.isRemoved()) {
                chain.tick(currentTick, defaultBroadcastRate);
            }
        }
        chains.entrySet().removeIf(e -> e.getValue().isRemoved());
    }

    @Override
    public IKHandle createChain(IKChainDefinition definition, Vector3 rootPosition) {
        String id = definition.id != null ? definition.id : "chain_" + System.nanoTime();
        if (chains.containsKey(id)) {
            LOGGER.warn("IK chain with id '{}' already exists, removing old one", id);
            removeChain(id);
        }
        IKChainInstance chain = new IKChainInstance(id, definition, rootPosition, this);
        chains.put(id, chain);
        broadcastChainCreation(chain);
        LOGGER.debug("Created IK chain '{}' with {} joints", id, definition.joints.size());
        return chain;
    }

    @Override
    public IKHandle createTwoBoneChain(String id, float upperLength, float lowerLength,
                                       Vector3 rootPosition, Vector3 poleVector) {
        IKChainDefinition def = IKChainDefinition.twoBone(id, upperLength, lowerLength, poleVector);
        return createChain(def, rootPosition);
    }

    @Override
    public IKHandle createSpiderLegChain(String id, float coxaLength, float femurLength,
                                         float tibiaLength, Vector3 rootPosition) {
        IKChainDefinition def = IKChainDefinition.spiderLeg(id, coxaLength, femurLength, tibiaLength);
        return createChain(def, rootPosition);
    }

    @Override
    public IKHandle createSpiderLegChainWithPole(String id, float coxaLength, float femurLength,
                                                 float tibiaLength, Vector3 rootPosition, Vector3 outwardDirection) {
        IKChainDefinition def = IKChainDefinition.spiderLegWithPole(id, coxaLength, femurLength, tibiaLength, outwardDirection);
        return createChain(def, rootPosition);
    }

    @Override
    public IKHandle createUniformChain(String id, int segmentCount, float segmentLength, Vector3 rootPosition) {
        IKChainDefinition def = new IKChainDefinition(id);
        for (int i = 0; i < segmentCount; i++) {
            def.addJoint("segment_" + i, segmentLength);
        }
        return createChain(def, rootPosition);
    }

    @Override
    public IKHandle getChain(String chainId) {
        return chains.get(chainId);
    }

    @Override
    public Collection<IKHandle> getAllChains() {
        return new ArrayList<>(chains.values());
    }

    @Override
    public Collection<IKHandle> getChainsForModel(long modelId) {
        return chains.values().stream()
                .filter(c -> c.getAttachedModelId() == modelId)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<IKHandle> getChainsForEntity(String entityUuid) {
        return chains.values().stream()
                .filter(c -> entityUuid.equals(c.getAttachedEntityUuid()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean removeChain(String chainId) {
        IKChainInstance chain = chains.remove(chainId);
        if (chain != null) {
            broadcastChainRemoval(chain);
            return true;
        }
        return false;
    }

    @Override
    public void removeAllChainsForModel(long modelId) {
        List<String> toRemove = chains.values().stream()
                .filter(c -> c.getAttachedModelId() == modelId)
                .map(IKChainInstance::getId)
                .collect(Collectors.toList());
        for (String id : toRemove) {
            removeChain(id);
        }
    }

    @Override
    public void removeAllChainsForEntity(String entityUuid) {
        List<String> toRemove = chains.values().stream()
                .filter(c -> entityUuid.equals(c.getAttachedEntityUuid()))
                .map(IKChainInstance::getId)
                .collect(Collectors.toList());
        for (String id : toRemove) {
            removeChain(id);
        }
    }

    @Override
    public IKChainState solveOnce(IKChainDefinition definition, Vector3 rootPosition, Vector3 targetPosition) {
        int jointCount = definition.joints.size() + 1;
        IKChainState state = new IKChainState("temp", jointCount);
        Vector3 pos = new Vector3(rootPosition);
        Vector3 direction = targetPosition.subtract(rootPosition).normalize();
        if (direction.lengthSquared() < 0.001f) {
            direction = Vector3.forward();
        }
        state.jointPositions.set(0, new Vector3(rootPosition));
        for (int i = 0; i < definition.joints.size(); i++) {
            float length = definition.joints.get(i).length;
            pos = pos.add(direction.multiply(length));
            state.jointPositions.set(i + 1, new Vector3(pos));
        }
        IKSolver solver = IKSolverFactory.getSolver(definition);
        solver.solve(state, targetPosition, rootPosition, definition);
        state.timestamp = getCurrentTick();
        return state;
    }

    @Override
    public long getCurrentTick() {
        return tickCounter.get();
    }

    @Override
    public void setDefaultBroadcastRate(int ticks) {
        this.defaultBroadcastRate = Math.max(1, ticks);
    }

    void removeChainInternal(IKChainInstance chain) {
        chains.remove(chain.getId());
        broadcastChainRemoval(chain);
    }

    void broadcastChainCreation(IKChainInstance chain) {
        if (packetSender == null) return;
        List<Float> boneLengths = new ArrayList<>();
        for (var joint : chain.getDefinition().joints) {
            boneLengths.add(joint.length);
        }
        S2C_IKCreateChainPacket packet = new S2C_IKCreateChainPacket(
                chain.getId(),
                chain.getState().getJointCount(),
                boneLengths,
                chain.getRootPosition(),
                chain.getDefinition().solverType.name(),
                chain.getAttachedModelId() >= 0 ? chain.getAttachedModelId() : null,
                chain.getAttachedEntityUuid() != null ? UUID.fromString(chain.getAttachedEntityUuid()) : null,
                chain.getAttachOffset()
        );
        packetSender.broadcastToAll(packet);
    }

    void broadcastChainUpdate(IKChainInstance chain) {
        if (packetSender == null) return;
        IKChainState state = chain.getState();
        List<IKJointData> joints = new ArrayList<>();
        for (int i = 0; i < state.getJointCount(); i++) {
            joints.add(new IKJointData(
                    state.getJointPosition(i),
                    state.getJointRotation(i)
            ));
        }
        S2C_IKUpdateChainPacket packet = new S2C_IKUpdateChainPacket(
                chain.getId(),
                joints,
                state.targetPosition,
                state.targetReached,
                state.timestamp
        );
        packetSender.broadcastToAll(packet);
    }

    void broadcastAttachment(IKChainInstance chain) {
        if (packetSender == null) return;
        S2C_IKAttachPacket packet = new S2C_IKAttachPacket(
                chain.getId(),
                chain.getAttachedModelId() >= 0 ? chain.getAttachedModelId() : null,
                chain.getAttachedEntityUuid() != null ? UUID.fromString(chain.getAttachedEntityUuid()) : null,
                chain.getAttachOffset()
        );
        packetSender.broadcastToAll(packet);
    }

    void broadcastDetachment(IKChainInstance chain) {
        if (packetSender == null) return;
        S2C_IKDetachPacket packet = new S2C_IKDetachPacket(chain.getId());
        packetSender.broadcastToAll(packet);
    }

    void broadcastChainRemoval(IKChainInstance chain) {
        if (packetSender == null) return;
        S2C_IKRemoveChainPacket packet = new S2C_IKRemoveChainPacket(chain.getId());
        packetSender.broadcastToAll(packet);
    }

    public void broadcastBatchUpdate(Collection<IKChainInstance> chainsToUpdate) {
        if (packetSender == null || chainsToUpdate.isEmpty()) return;
        List<IKChainBatchEntry> entries = new ArrayList<>();
        for (IKChainInstance chain : chainsToUpdate) {
            IKChainState state = chain.getState();
            List<IKJointData> joints = new ArrayList<>();
            for (int i = 0; i < state.getJointCount(); i++) {
                joints.add(new IKJointData(
                        state.getJointPosition(i),
                        state.getJointRotation(i)
                ));
            }
            entries.add(new IKChainBatchEntry(
                    chain.getId(),
                    joints,
                    state.targetPosition,
                    state.targetReached
            ));
        }
        S2C_IKBatchUpdatePacket packet = new S2C_IKBatchUpdatePacket(entries, getCurrentTick());
        packetSender.broadcastToAll(packet);
    }

    public interface IKPacketSender {
        void broadcastToAll(Object packet);

        void sendToPlayer(Player player, Object packet);
    }
}