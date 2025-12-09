package com.moud.client.model;

import com.moud.client.collision.ClientCollisionManager;
import com.moud.client.collision.ModelCollisionManager;
import com.moud.client.editor.runtime.RuntimeObjectRegistry;
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
            loadModelData(model);
            RuntimeObjectRegistry.getInstance().syncModel(model);
            return model;
        });
    }

    private void loadModelData(RenderableModel model) {
        Identifier modelIdentifier = resolveModelIdentifier(model.getModelPath());
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

    private Identifier resolveModelIdentifier(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        String normalized = rawPath.trim().replace('\\', '/');
        if (normalized.startsWith("moud:moud/")) {
            normalized = "moud:" + normalized.substring("moud:moud/".length());
        }
        if (!normalized.contains(":")) {
            normalized = normalized.startsWith("moud/") ? "moud:" + normalized.substring(5) : "moud:" + normalized;
        }
        if (normalized.startsWith("moud:/")) {
            normalized = "moud:" + normalized.substring("moud:/".length());
        }
        Identifier identifier = Identifier.tryParse(normalized);
        if (identifier == null) {
            return null;
        }
        if ("moud".equals(identifier.getNamespace())) {
            String path = identifier.getPath();
            while (path.startsWith("moud/")) {
                path = path.substring(5);
            }
            if (path.isEmpty()) {
                return null;
            }
            return Identifier.of("moud", path);
        }
        return identifier;
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
