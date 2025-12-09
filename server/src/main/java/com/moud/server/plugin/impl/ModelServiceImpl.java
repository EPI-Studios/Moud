package com.moud.server.plugin.impl;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.models.ModelData;
import com.moud.plugin.api.services.ModelService;
import com.moud.plugin.api.services.model.ModelDefinition;
import com.moud.plugin.api.services.model.ModelHandle;
import com.moud.server.entity.ModelManager;
import com.moud.server.instance.InstanceManager;
import com.moud.server.proxy.ModelProxy;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelServiceImpl implements ModelService {
    private final Logger logger;
    private final Map<Long, ModelHandleImpl> models = new ConcurrentHashMap<>();
    private final Map<String, ModelData> modelDataMap = new ConcurrentHashMap<>();

    public ModelServiceImpl(Logger logger) {
        this.logger = logger;
    }

    @Override
    public ModelHandle spawn(ModelDefinition definition) {
        String modelId = definition.modelData().modelId();
        modelDataMap.putIfAbsent(modelId, definition.modelData());
        String modelPath = definition.modelData().modelPath();
        String texturePath = definition.modelData().texturePath();
        ModelProxy proxy = new ModelProxy(
                definition.instance() != null ? definition.instance() : InstanceManager.getInstance().getDefaultInstance(),
                modelPath,
                definition.position(),
                definition.rotation(),
                definition.scale(),
                texturePath
        );
        ModelHandleImpl handle = new ModelHandleImpl(proxy, this);
        models.put(handle.id(), handle);
        logger.info("Spawned plugin model {} at {}", proxy.getId(), proxy.getPosition());
        return handle;
    }

    @Override
    public Optional<ModelHandle> get(long id) {
        return Optional.ofNullable(models.get(id));
    }

    @Override
    public Collection<ModelHandle> all() {
        return List.copyOf(models.values());
    }

    /**
     * Get all registered model data.
     * @return Map of model ID to ModelData
     */
    @Override
    public Map<String, ModelData> AllModelData() {
        return Map.copyOf(modelDataMap);
    }

    /**
     * Get model data by model ID.
     * @param modelId The model ID
     * @return The ModelData, or null if not found
     */
    @Override
    public ModelData getModelData(String modelId) {
        return modelDataMap.get(modelId);
    }

    /**
     * Register model data.
     * @param data The ModelData to register
     * @return The registered ModelData
     */
    @Override
    public ModelData register(ModelData data) {
        modelDataMap.putIfAbsent(data.modelId(), data);
        return data;
    }

    void remove(long id) {
        models.remove(id);
    }

    public void shutdown() {
        models.values().forEach(ModelHandleImpl::remove);
        models.clear();
    }

    private static final class ModelHandleImpl implements ModelHandle {
        private final ModelProxy proxy;
        private final ModelServiceImpl owner;

        private ModelHandleImpl(ModelProxy proxy, ModelServiceImpl owner) {
            this.proxy = proxy;
            this.owner = owner;
        }

        @Override
        public long id() {
            return proxy.getId();
        }

        @Override
        public Vector3 position() {
            return proxy.getPosition();
        }

        @Override
        public Quaternion rotation() {
            return proxy.getRotation();
        }

        @Override
        public Vector3 scale() {
            return proxy.getScale();
        }

        @Override
        public void setPosition(Vector3 position) {
            proxy.setPosition(position);
        }

        @Override
        public void setRotation(Quaternion rotation) {
            proxy.setRotation(rotation);
        }

        @Override
        public void setScale(Vector3 scale) {
            proxy.setScale(scale);
        }

        @Override
        public void setTexture(String texturePath) {
            proxy.setTexture(texturePath);
        }

        @Override
        public void remove() {
            proxy.remove();
            owner.remove(id());
        }
    }
}
