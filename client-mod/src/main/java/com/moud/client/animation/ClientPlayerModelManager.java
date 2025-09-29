package com.moud.client.animation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientPlayerModelManager {
    private static final ClientPlayerModelManager INSTANCE = new ClientPlayerModelManager();
    private final Map<Long, AnimatedPlayerModel> models = new ConcurrentHashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientPlayerModelManager.class);

    private ClientPlayerModelManager() {}

    public static ClientPlayerModelManager getInstance() {
        return INSTANCE;
    }

    public AnimatedPlayerModel createModel(long modelId) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (!client.isOnThread()) {
            return client.submit(() -> createModel(modelId)).join();
        }

        ClientWorld world = client.world;
        if (world == null) {
            LOGGER.warn("Tried to create player model {} without an active client world", modelId);
            return null;
        }

        return models.computeIfAbsent(modelId, id -> new AnimatedPlayerModel(world));
    }

    public void removeModel(long modelId) {
        models.remove(modelId);
    }

    public AnimatedPlayerModel getModel(long modelId) {
        return models.get(modelId);
    }

    public Collection<AnimatedPlayerModel> getModels() {
        return models.values();
    }

    public void clear() {
        models.clear();
    }
}