package com.moud.server.plugin.impl;

import com.moud.api.math.Vector3;
import com.moud.plugin.api.services.PhysicsController;
import com.moud.server.entity.ModelManager;
import com.moud.server.physics.PhysicsService;
import com.moud.server.proxy.ModelProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public final class PhysicsControllerImpl implements PhysicsController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PhysicsControllerImpl.class);

    private final PhysicsService physicsService = PhysicsService.getInstance();

    @Override
    public boolean supported() {
        return physicsService != null;
    }

    @Override
    public void attachDynamic(long modelId, PhysicsBodyDefinition definition) {
        if (!supported()) return;
        ModelProxy proxy = ModelManager.getInstance().getById(modelId);
        if (proxy == null) {
            LOGGER.warn("Cannot attach physics to unknown model {}", modelId);
            return;
        }
        Vector3 extents = definition.halfExtents();
        physicsService.attachDynamicModel(proxy, extents, definition.mass(), definition.initialVelocity(), definition.allowPlayerPush());
    }

    @Override
    public void detach(long modelId) {
        if (!supported()) return;
        ModelProxy proxy = ModelManager.getInstance().getById(modelId);
        if (proxy != null) {
            physicsService.detachModel(proxy);
        }
    }

    @Override
    public void attachFollow(long modelId, String entityUuid, Vector3 offset, boolean kinematic) {
        if (!supported() || entityUuid == null || entityUuid.isBlank()) return;
        ModelProxy proxy = ModelManager.getInstance().getById(modelId);
        if (proxy == null) return;
        try {
            physicsService.attachFollow(proxy, java.util.UUID.fromString(entityUuid), offset, kinematic);
        } catch (Exception e) {
            LOGGER.warn("Failed to attach follow for model {} to {}", modelId, entityUuid, e);
        }
    }

    @Override
    public void attachSpring(long modelId, Vector3 anchor, double stiffness, double damping, Double restLength) {
        if (!supported() || anchor == null) return;
        ModelProxy proxy = ModelManager.getInstance().getById(modelId);
        if (proxy == null) return;
        physicsService.attachSpring(proxy, anchor, stiffness, damping, restLength);
    }

    @Override
    public void clearConstraints(long modelId) {
        if (!supported()) return;
        ModelProxy proxy = ModelManager.getInstance().getById(modelId);
        if (proxy == null) return;
        physicsService.clearConstraints(proxy);
    }

    @Override
    public PhysicsState state(long modelId) {
        if (!supported()) return null;
        ModelProxy proxy = ModelManager.getInstance().getById(modelId);
        if (proxy == null) return null;
        var state = physicsService.getState(proxy);
        if (state == null) return null;
        return new PhysicsState(
                state.linearVelocity(),
                state.angularVelocity(),
                state.active(),
                state.onGround(),
                state.lastImpulse(),
                state.hasFollowConstraint(),
                state.hasSpringConstraint()
        );
    }
}
