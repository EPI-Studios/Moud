package com.moud.client.model;

import com.moud.client.collision.ClientCollisionManager;
import com.moud.client.collision.ModelCollisionManager;
import com.moud.client.editor.runtime.RuntimeObjectRegistry;
import com.moud.client.util.IdentifierUtils;
import com.moud.client.util.OBJLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ClientModelManager {
    private static final ClientModelManager INSTANCE = new ClientModelManager();
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientModelManager.class);
    private final Map<Long, RenderableModel> models = new ConcurrentHashMap<>();

    private ClientModelManager() {}

    public static ClientModelManager getInstance() {
        return INSTANCE;
    }

    public void createModel(long id, String modelPath) {
        models.computeIfAbsent(id, key -> {
            LOGGER.info("Creating client-side model ID {} with path {}", id, modelPath);
            RenderableModel model = new RenderableModel(id, modelPath);
            model.setSmoothingDurationTicks(3.0f);
            loadModelData(model);
            RuntimeObjectRegistry.getInstance().syncModel(model);
            return model;
        });
    }

    public void reloadModels() {
        if (models.isEmpty()) return;
        LOGGER.info("Reloading {} client-side models...", models.size());
        for (RenderableModel model : models.values()) {
            loadModelData(model);
        }
    }

    private void loadModelData(RenderableModel model) {
        Identifier modelIdentifier = IdentifierUtils.resolveModelIdentifier(model.getModelPath());
        if (modelIdentifier == null) {
            LOGGER.error("Invalid model identifier for model {}: {}", model.getId(), model.getModelPath());
            return;
        }

        MinecraftClient.getInstance().execute(() -> {
            try {
                Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(modelIdentifier);
                if (resource.isPresent()) {
                    try (InputStream inputStream = resource.get().getInputStream()) {
                        OBJLoader.OBJMesh meshData = OBJLoader.load(inputStream);
                        model.uploadMesh(meshData);
                        ModelCollisionManager.getInstance().sync(model);
                        RuntimeObjectRegistry.getInstance().syncModel(model);
                        LOGGER.info("Successfully loaded and uploaded model data for {}", model.getModelPath());
                    }
                } else {
                    LOGGER.error("Could not find model resource at path: {}", modelIdentifier);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load model resource for path: {}", modelIdentifier, e);
            }
        });
    }

    public void removeModel(long id) {
        RenderableModel model = models.remove(id);
        if (model != null) {
            model.destroy();
            LOGGER.info("Removed client-side model ID {}", id);
        }
        ModelCollisionManager.getInstance().removeModel(id);
        ClientCollisionManager.unregisterModel(id);
        RuntimeObjectRegistry.getInstance().removeModel(id);
    }

    public RenderableModel getModel(long id) {
        return models.get(id);
    }

    public Collection<RenderableModel> getModels() {
        return models.values();
    }

    public void clear() {
        for (Long id : new ArrayList<>(models.keySet())) {
            removeModel(id);
        }
        models.clear();
        ClientCollisionManager.clear();
        ModelCollisionManager.getInstance().clear();
        LOGGER.info("Cleared all client-side models.");
    }
}
