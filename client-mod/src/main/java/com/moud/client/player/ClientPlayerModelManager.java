package com.moud.client.player;

import com.moud.api.math.Vector3;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

public class ClientPlayerModelManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientPlayerModelManager.class);
    private static ClientPlayerModelManager instance;

    private final Map<Long, ManagedPlayerModel> models = new ConcurrentHashMap<>();
    private final MinecraftClient client;

    private ClientPlayerModelManager() {
        this.client = MinecraftClient.getInstance();
    }

    public static synchronized ClientPlayerModelManager getInstance() {
        if (instance == null) {
            instance = new ClientPlayerModelManager();
        }
        return instance;
    }

    public void createModel(long modelId, Vector3 position, String skinUrl) {
        ManagedPlayerModel model = new ManagedPlayerModel(modelId, position, skinUrl);
        models.put(modelId, model);
        LOGGER.debug("Created player model {} at position {}", modelId, position);
    }

    public void updateModel(long modelId, Vector3 position, float yaw, float pitch) {
        ManagedPlayerModel model = models.get(modelId);
        if (model != null) {
            model.setPosition(position);
            model.setRotation(yaw, pitch);
        }
    }

    public void updateSkin(long modelId, String skinUrl) {
        ManagedPlayerModel model = models.get(modelId);
        if (model != null) {
            model.setSkinUrl(skinUrl);
        }
    }

    public void playAnimation(long modelId, String animationName) {
        ManagedPlayerModel model = models.get(modelId);
        if (model != null) {
            model.setCurrentAnimation(animationName);
        }
    }

    public void removeModel(long modelId) {
        models.remove(modelId);
        LOGGER.debug("Removed player model {}", modelId);
    }

    public ManagedPlayerModel getModel(long modelId) {
        return models.get(modelId);
    }

    public Map<Long, ManagedPlayerModel> getAllModels() {
        return new ConcurrentHashMap<>(models);
    }

    public void cleanup() {
        models.clear();
    }

    public static class ManagedPlayerModel {
        private final long modelId;
        private Vector3 position;
        private float yaw;
        private float pitch;
        private String skinUrl;
        private String currentAnimation;
        private Identifier skinTexture;
        private PlayerEntityModel<net.minecraft.entity.player.PlayerEntity> model;

        public ManagedPlayerModel(long modelId, Vector3 position, String skinUrl) {
            this.modelId = modelId;
            this.position = position;
            this.skinUrl = skinUrl;
            this.yaw = 0.0f;
            this.pitch = 0.0f;
            initializeModel();
            loadSkinTexture();
        }

        private void initializeModel() {

            ModelPart root = PlayerEntityModel.getTexturedModelData(Dilation.NONE, false).getRoot().createPart(64, 64);
            this.model = new PlayerEntityModel<>(root, false);
        }

        private void loadSkinTexture() {
            try {
                GameProfile profile = new GameProfile(UUID.randomUUID(), "PlayerModel_" + modelId);
                this.skinTexture = MinecraftClient.getInstance().getSkinProvider().getSkinTextures(profile).texture();
            } catch (Exception e) {
                this.skinTexture = Identifier.of("textures/entity/player/wide/steve.png");
            }
        }

        public long getModelId() {
            return modelId;
        }

        public Vector3 getPosition() {
            return position;
        }

        public void setPosition(Vector3 position) {
            this.position = position;
        }

        public float getYaw() {
            return yaw;
        }

        public float getPitch() {
            return pitch;
        }

        public void setRotation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public String getSkinUrl() {
            return skinUrl;
        }

        public void setSkinUrl(String skinUrl) {
            this.skinUrl = skinUrl;
            loadSkinTexture();
        }

        public String getCurrentAnimation() {
            return currentAnimation;
        }

        public void setCurrentAnimation(String currentAnimation) {
            this.currentAnimation = currentAnimation;
        }

        public Identifier getSkinTexture() {
            return skinTexture;
        }

        public PlayerEntityModel<net.minecraft.entity.player.PlayerEntity> getModel() {
            return model;
        }

        public boolean isInBounds(double x, double y, double z, double width, double height, double depth) {
            return x >= position.x - width/2 && x <= position.x + width/2 &&
                    y >= position.y && y <= position.y + height &&
                    z >= position.z - depth/2 && z <= position.z + depth/2;
        }
    }
}