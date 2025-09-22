package com.moud.client.animation;

import net.minecraft.client.MinecraftClient;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientPlayerModelManager {
    private static final ClientPlayerModelManager INSTANCE = new ClientPlayerModelManager();
    private final Map<Long, AnimatedPlayerModel> models = new ConcurrentHashMap<>();

    private ClientPlayerModelManager() {}

    public static ClientPlayerModelManager getInstance() {
        return INSTANCE;
    }

    public void createModel(long modelId) {
        MinecraftClient.getInstance().execute(() -> {
            models.put(modelId, new AnimatedPlayerModel(MinecraftClient.getInstance().world));
        });
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