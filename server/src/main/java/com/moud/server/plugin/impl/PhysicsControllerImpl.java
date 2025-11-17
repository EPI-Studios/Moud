package com.moud.server.plugin.impl;

import com.moud.api.math.Vector3;
import com.moud.plugin.api.services.PhysicsController;
import com.moud.server.entity.ModelManager;
import com.moud.server.physics.PhysicsService;
import com.moud.server.proxy.ModelProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        physicsService.attachDynamicModel(proxy, extents, definition.mass(), definition.initialVelocity());
    }

    @Override
    public void detach(long modelId) {
        if (!supported()) return;
        ModelProxy proxy = ModelManager.getInstance().getById(modelId);
        if (proxy != null) {
            physicsService.detachModel(proxy);
        }
    }
}
