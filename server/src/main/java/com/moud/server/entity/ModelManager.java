package com.moud.server.entity;

import com.moud.server.proxy.ModelProxy;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ModelManager {
    private static final ModelManager INSTANCE = new ModelManager();
    private static final AtomicLong ID_COUNTER = new AtomicLong(0);

    private final Map<Long, ModelProxy> modelsById = new ConcurrentHashMap<>();
    private final Map<UUID, ModelProxy> modelsByEntityUuid = new ConcurrentHashMap<>();
    private final Map<Long, SceneBinding> sceneBindings = new ConcurrentHashMap<>();

    private ModelManager() {}

    public static ModelManager getInstance() {
        return INSTANCE;
    }

    public long nextId() {
        return ID_COUNTER.incrementAndGet();
    }

    public void register(ModelProxy model) {
        modelsById.put(model.getId(), model);
        modelsByEntityUuid.put(model.getEntity().getUuid(), model);
    }

    public void unregister(ModelProxy model) {
        modelsById.remove(model.getId());
        modelsByEntityUuid.remove(model.getEntity().getUuid());
        sceneBindings.remove(model.getId());
    }

    @Nullable
    public ModelProxy getById(long id) {
        return modelsById.get(id);
    }

    @Nullable
    public ModelProxy getByEntity(Entity entity) {
        return modelsByEntityUuid.get(entity.getUuid());
    }

    @Nullable
    public ModelProxy getByEntityUuid(UUID uuid) {
        return modelsByEntityUuid.get(uuid);
    }

    public Collection<ModelProxy> getAllModels() {
        return modelsById.values();
    }
    public void clear() {
        modelsById.values().forEach(ModelProxy::remove);
        modelsById.clear();
        modelsByEntityUuid.clear();
        sceneBindings.clear();
    }

    public void tagSceneBinding(long modelId, String sceneId, String objectId) {
        sceneBindings.put(modelId, new SceneBinding(sceneId, objectId));
    }

    public SceneBinding getSceneBinding(long modelId) {
        return sceneBindings.get(modelId);
    }

    public record SceneBinding(String sceneId, String objectId) {
    }
}
