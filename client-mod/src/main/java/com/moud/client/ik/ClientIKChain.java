package com.moud.client.ik;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClientIKChain {
    private final String id;
    private final int jointCount;
    private final List<Float> boneLengths;
    private final String solverType;
    private Vector3 rootPosition;
    private volatile Vector3 targetPosition = Vector3.zero();
    private volatile List<MoudPackets.IKJointData> joints = Collections.emptyList();
    private volatile boolean targetReached = false;
    private volatile long timestamp = 0L;
    private volatile Long attachedModelId;
    private volatile java.util.UUID attachedEntityUuid;
    private volatile Vector3 attachOffset;

    public ClientIKChain(MoudPackets.S2C_IKCreateChainPacket packet) {
        this.id = packet.chainId();
        this.jointCount = packet.jointCount();
        this.boneLengths = packet.boneLengths();
        this.rootPosition = packet.rootPosition();
        this.solverType = packet.solverType();

        List<MoudPackets.IKJointData> initialJoints = new ArrayList<>();
        for (int i = 0; i < jointCount; i++) {
            initialJoints.add(new MoudPackets.IKJointData(packet.rootPosition(), null));
        }
        this.joints = initialJoints;
    }

    public void update(MoudPackets.S2C_IKUpdateChainPacket packet) {
        this.targetPosition = packet.targetPosition();
        this.joints = packet.joints();
        this.targetReached = packet.targetReached();
        this.timestamp = packet.timestamp();
    }

    public void update(MoudPackets.IKChainBatchEntry entry) {
        this.targetPosition = entry.targetPosition();
        this.joints = entry.joints();
        this.targetReached = entry.targetReached();
    }

    public void updateTarget(MoudPackets.S2C_IKUpdateTargetPacket packet) {
        this.targetPosition = packet.targetPosition();
        this.timestamp = packet.timestamp();
    }

    public void updateRoot(MoudPackets.S2C_IKUpdateRootPacket packet) {
        this.rootPosition = packet.rootPosition();
        this.timestamp = packet.timestamp();
    }

    public void attach(MoudPackets.S2C_IKAttachPacket packet) {
        this.attachedModelId = packet.modelId();
        this.attachedEntityUuid = packet.entityUuid();
        this.attachOffset = packet.offset();
    }

    public void detach() {
        this.attachedModelId = null;
        this.attachedEntityUuid = null;
        this.attachOffset = null;
    }

    public String getId() {
        return id;
    }

    public List<MoudPackets.IKJointData> getJoints() {
        return joints;
    }

    public Vector3 getTargetPosition() {
        return targetPosition;
    }

    public Vector3 getRootPosition() {
        return rootPosition;
    }

    public boolean isTargetReached() {
        return targetReached;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Long getAttachedModelId() {
        return attachedModelId;
    }

    public java.util.UUID getAttachedEntityUuid() {
        return attachedEntityUuid;
    }

    public Vector3 getAttachOffset() {
        return attachOffset;
    }
}
