package com.moud.client.player;

import com.moud.api.math.Vector3;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
        client.execute(() -> {
            ManagedPlayerModel model = new ManagedPlayerModel(modelId, position, skinUrl);
            models.put(modelId, model);
            LOGGER.info("Created player model {} at position {} with skin {}", modelId, position, skinUrl);
        });
    }

    public void updateModel(long modelId, Vector3 position, float yaw, float pitch) {
        client.execute(() -> {
            ManagedPlayerModel model = models.get(modelId);
            if (model != null) {
                model.setPosition(position);
                model.setRotation(yaw, pitch);
            } else {
                LOGGER.warn("Attempted to update non-existent model {}", modelId);
            }
        });
    }

    public void updateSkin(long modelId, String skinUrl) {
        client.execute(() -> {
            ManagedPlayerModel model = models.get(modelId);
            if (model != null) {
                model.setSkinUrl(skinUrl);
            }
        });
    }

    public void playAnimation(long modelId, String animationName) {
        client.execute(() -> {
            ManagedPlayerModel model = models.get(modelId);
            if (model != null) {
                model.setCurrentAnimation(animationName);
            }
        });
    }

    public void removeModel(long modelId) {
        client.execute(() -> {
            models.remove(modelId);
            LOGGER.info("Removed player model {}", modelId);
        });
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

    public static class ManagedPlayerModel implements com.moud.client.player.ManagedPlayerModel {
        private final long modelId;
        private Vector3 position;
        private float yaw;
        private float pitch;
        private String skinUrl;
        private String currentAnimation;
        private Identifier skinTexture;
        private Float scale = 1.0F;
        private final Map<String, Boolean> partVisibility = new ConcurrentHashMap<>();
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
            try {
                ModelPart root = PlayerEntityModel.getTexturedModelData(Dilation.NONE, false).getRoot().createPart(64, 64);
                this.model = new PlayerEntityModel<>(root, false);
            } catch (Exception e) {
                LOGGER.error("Failed to initialize player model", e);
            }
        }

        private void loadSkinTexture() {
            try {
                if (skinUrl != null && !skinUrl.isEmpty()) {
                    GameProfile profile = new GameProfile(UUID.randomUUID(), "PlayerModel_" + modelId);
                    this.skinTexture = MinecraftClient.getInstance().getSkinProvider().getSkinTextures(profile).texture();
                } else {
                    this.skinTexture = Identifier.of("textures/entity/player/wide/steve.png");
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load custom skin, using default", e);
                this.skinTexture = Identifier.of("textures/entity/player/wide/steve.png");
            }
        }

//        // **** LA CORRECTION EST ICI ****
//        // La méthode s'appelle maintenant getId() et retourne un Long
//        @Override
//        public Long getId() {
//            return this.modelId;
//        }
//
//        @Override
//        public PlayerEntityModel<net.minecraft.entity.player.PlayerEntity> getModel() {
//            return model;
//        }
//
//        @Override
//        public Vec3d getPosition() { return new Vec3d(position.x, position.y, position.z); }
//        @Override
//        public float getYaw() { return yaw; }
//        @Override
//        public Float getPitch() { return pitch; }
//        @Override
//        public Float getScale() { return this.scale; }
//        @Override
//        public Float getLimbAngle() { return 0.0F; }
//        @Override
//        public Float getLimbDistance() { return 0.0F; }
//        @Override
//        public Identifier getSkinTexture() { return skinTexture; }
//        @Override
//        public Map<String, PartAnimation> getPartAnimations() { return Collections.emptyMap(); }
//        @Override
//        public Map<String, RenderLayer> getPartRenderLayers() { return Collections.emptyMap(); }
//        @Override
//        public boolean isPartVisible(String partName) { return this.partVisibility.getOrDefault(partName, true); }
//
//        @Override
//        public long getModelId() {
//            return modelId;
//        }

        public void setPosition(Vector3 position) { this.position = position; }
        public void setRotation(float yaw, float pitch) { this.yaw = yaw; this.pitch = pitch; }
        public void setSkinUrl(String skinUrl) { this.skinUrl = skinUrl; loadSkinTexture(); }
        public String getSkinUrl() { return skinUrl; }
        public String getCurrentAnimation() { return currentAnimation; }
        public void setCurrentAnimation(String currentAnimation) { this.currentAnimation = currentAnimation; }
    }
}