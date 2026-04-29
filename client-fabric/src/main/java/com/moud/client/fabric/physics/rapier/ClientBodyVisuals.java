package com.moud.client.fabric.physics.rapier;

import com.moud.physics.api.Transform;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientBodyVisuals {

    private final BodyInterpolator interpolator;
    private final Map<Long, Long> nodeToBody = new ConcurrentHashMap<>();
    private final Map<Long, Long> bodyToNode = new ConcurrentHashMap<>();

    public ClientBodyVisuals(BodyInterpolator interpolator) {
        this.interpolator = interpolator;
    }

    public void register(long nodeId, long bodyId) {
        nodeToBody.put(nodeId, bodyId);
        bodyToNode.put(bodyId, nodeId);
    }

    public void unregister(long bodyId) {
        Long nodeId = bodyToNode.remove(bodyId);
        if (nodeId != null) nodeToBody.remove(nodeId);
        interpolator.remove(bodyId);
    }

    public void clear() {
        nodeToBody.clear();
        bodyToNode.clear();
        interpolator.clear();
    }

    public Optional<Transform> sampleByNode(long nodeId, long renderTimeMs) {
        Long bodyId = nodeToBody.get(nodeId);
        if (bodyId == null) return Optional.empty();
        return interpolator.sample(bodyId, renderTimeMs);
    }

    public Optional<Transform> sampleByBody(long bodyId, long renderTimeMs) {
        return interpolator.sample(bodyId, renderTimeMs);
    }

    public BodyInterpolator interpolator() {
        return interpolator;
    }
}
