package com.moud.server.plugin.impl;

import com.moud.plugin.api.services.LightingService;
import com.moud.plugin.api.services.lighting.LightHandle;
import com.moud.plugin.api.services.lighting.PointLightDefinition;
import com.moud.server.lighting.ServerLightingManager;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class LightingServiceImpl implements LightingService {
    private final Logger logger;
    private final ServerLightingManager lightingManager = ServerLightingManager.getInstance();
    private final Map<Long, LightHandleImpl> lights = new ConcurrentHashMap<>();

    public LightingServiceImpl(Logger logger) {
        this.logger = logger;
    }

    @Override
    public LightHandle create(PointLightDefinition definition) {
        long id = lightingManager.spawnLight(definition.type(), toPayload(definition));
        LightHandleImpl handle = new LightHandleImpl(id, definition, this);
        lights.put(id, handle);
        logger.info("Created plugin light {} of type {}", id, definition.type());
        return handle;
    }

    @Override
    public Optional<LightHandle> get(long id) {
        return Optional.ofNullable(lights.get(id));
    }

    @Override
    public Collection<LightHandle> all() {
        return List.copyOf(lights.values());
    }

    void remove(long id) {
        lights.remove(id);
    }

    public void shutdown() {
        lights.values().forEach(LightHandleImpl::remove);
        lights.clear();
    }

    private Map<String, Object> toPayload(PointLightDefinition definition) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", definition.type());
        payload.put("x", definition.position().x);
        payload.put("y", definition.position().y);
        payload.put("z", definition.position().z);
        payload.put("dirX", definition.direction().x);
        payload.put("dirY", definition.direction().y);
        payload.put("dirZ", definition.direction().z);
        payload.put("r", definition.red());
        payload.put("g", definition.green());
        payload.put("b", definition.blue());
        payload.put("brightness", definition.brightness());
        payload.put("radius", definition.radius());
        return payload;
    }

    private final class LightHandleImpl implements LightHandle {
        private final long id;
        private PointLightDefinition definition;
        private final LightingServiceImpl owner;

        private LightHandleImpl(long id, PointLightDefinition definition, LightingServiceImpl owner) {
            this.id = id;
            this.definition = definition;
            this.owner = owner;
        }

        @Override
        public long id() {
            return id;
        }

        @Override
        public PointLightDefinition definition() {
            return definition;
        }

        @Override
        public void update(PointLightDefinition definition) {
            this.definition = definition;
            lightingManager.createOrUpdateLight(id, toPayload(definition));
        }

        @Override
        public void remove() {
            lightingManager.removeLight(id);
            owner.remove(id);
        }
    }
}
