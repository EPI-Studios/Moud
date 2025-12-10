package com.moud.client.animation;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.moud.api.math.Vector3;
import com.zigythebird.playeranim.accessors.IAnimatedPlayer;
import com.zigythebird.playeranim.animation.PlayerAnimManager;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class AnimatedPlayerModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnimatedPlayerModel.class);

    private final OtherClientPlayerEntity playerEntity;
    private final PlayerAnimationController animationController;
    private final PlayerEntityModel<OtherClientPlayerEntity> model;

    private double x, y, z;
    private double prevX, prevY, prevZ;
    private float yaw, pitch;
    private float prevYaw, prevPitch;
    private boolean hasPosition;
    private MovementState currentState = MovementState.IDLE;
    private String overrideAnimation = null;
    private long overrideAnimationEndTime = 0;
    private final long modelId;
    public AnimatedPlayerModel(ClientWorld world) {
        MinecraftClient client = MinecraftClient.getInstance();
        this.model = new PlayerEntityModel<>(client.getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER), false);

        GameProfile gameProfile = new GameProfile(UUID.randomUUID(), "MoudModel_" + this.hashCode());
        this.playerEntity = new OtherClientPlayerEntity(world, gameProfile);
        this.modelId = gameProfile.getId().getLeastSignificantBits() ^ gameProfile.getId().getMostSignificantBits();

        this.animationController = new PlayerAnimationController(this.playerEntity, (controller, state, animSetter) -> PlayState.CONTINUE);

        PlayerAnimManager manager = ((IAnimatedPlayer) this.playerEntity).playerAnimLib$getAnimManager();

        manager.addAnimLayer(1000, this.animationController);


        LOGGER.debug("AnimatedPlayerModel created. Controller hash: {}", this.animationController.hashCode());
    }

    public long getModelId() {
        return modelId;
    }

    public void tick() {

        this.playerEntity.tick();

        if (overrideAnimation != null && System.currentTimeMillis() > overrideAnimationEndTime) {
            LOGGER.debug("Override animation '{}' ended. Returning to base state: {}", overrideAnimation, currentState);
            overrideAnimation = null;
            this.animationController.stop();
        }
    }

    private void setState(MovementState newState) {
        if (currentState == newState) return;
        LOGGER.debug("Model state change: {} -> {}", currentState, newState);
        this.currentState = newState;
        if (overrideAnimation == null) {
            //
        }
    }


    public void playAnimationWithFade(String animationIdStr, int durationTicks) {
        if (animationIdStr == null || animationIdStr.isBlank()) {
            this.overrideAnimation = null;
            this.animationController.stop();
            return;
        }
        playAnimation(animationIdStr);
    }

    public void playAnimation(String animationIdStr) {
        if (animationIdStr == null || animationIdStr.isBlank()) {
            this.overrideAnimation = null;
            this.animationController.stop();
            return;
        }
        Identifier animationId = Identifier.tryParse(animationIdStr);
        if (animationId == null) {
            animationId = Identifier.of("moud", animationIdStr);
        }

        Animation animation = PlayerAnimResources.getAnimation(animationId);
        if (animation != null) {

            this.overrideAnimation = animationIdStr;
            long duration;
            duration = Math.max(250, (long) (animation.length() * 50));
            this.overrideAnimationEndTime = Long.MAX_VALUE;
            animationController.triggerAnimation(RawAnimation.begin().thenPlay(animation));
            LOGGER.debug("Triggering one-shot animation '{}'. Duration: {}ms", animationId, duration);
        }

    }

    private Identifier resolveAnimationId(String raw) {
        Identifier primary = Identifier.tryParse(raw);
        if (primary != null && PlayerAnimResources.getAnimation(primary) != null) {
            return primary;
        }
        // Try strip namespace/path variants for common cases
        String path = raw;
        if (raw.contains(":")) {
            path = raw.substring(raw.indexOf(":") + 1);
        }
        String basename = path.contains("/") ? path.substring(path.lastIndexOf("/") + 1) : path;
        Identifier[] candidates = new Identifier[] {
                Identifier.of("moud", path),
                Identifier.of("moud", "player_animations/" + basename),
                Identifier.of("moud", basename)
        };
        for (Identifier id : candidates) {
            if (PlayerAnimResources.getAnimation(id) != null) {
                return id;
            }
        }
        return primary != null ? primary : Identifier.of("moud", basename);
    }

    public void setupAnim(float partialTick) {
        playerEntity.setPos(getInterpolatedX(partialTick), getInterpolatedY(partialTick), getInterpolatedZ(partialTick));
        playerEntity.setYaw(getInterpolatedYaw(partialTick));
        playerEntity.setPitch(getInterpolatedPitch(partialTick));
        playerEntity.headYaw = playerEntity.getYaw();
        playerEntity.bodyYaw = playerEntity.getYaw();

        model.setAngles(playerEntity, 0, 0, 0, playerEntity.getYaw(), playerEntity.getPitch());
        LOGGER.trace("SetupAnim called for Model. Controller Active: {}", animationController.isActive());

    }

    public OtherClientPlayerEntity getEntity() {
        return this.playerEntity;
    }

    public PlayerEntityModel<OtherClientPlayerEntity> getModel() {
        return model;
    }

    public PlayerAnimationController getAnimationController() {
        return animationController;
    }

    public void updateSkin(String skinUrl) {
        if (skinUrl == null || skinUrl.isEmpty()) {
            LOGGER.warn("Empty skin URL provided for player model");
            return;
        }
        try {
            GameProfile gameProfile = this.playerEntity.getGameProfile();
            gameProfile.getProperties().clear();
            String textureData = String.format("{\"textures\":{\"SKIN\":{\"url\":\"%s\"}}}", skinUrl);
            String encoded = java.util.Base64.getEncoder().encodeToString(textureData.getBytes());
            gameProfile.getProperties().put("textures", new Property("textures", encoded));
            LOGGER.info("Applied skin URL to player model: {}", skinUrl);
        } catch (Exception e) {
            LOGGER.error("Failed to apply skin to player model", e);
        }
    }

    public void updatePositionAndRotation(Vector3 position, float yaw, float pitch) {
        float wrappedYaw = MathHelper.wrapDegrees(yaw);
        float wrappedPitch = MathHelper.wrapDegrees(pitch);
        this.prevX = position.x;
        this.prevY = position.y;
        this.prevZ = position.z;
        this.prevYaw = wrappedYaw;
        this.prevPitch = wrappedPitch;
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        this.yaw = wrappedYaw;
        this.pitch = wrappedPitch;
        this.hasPosition = true;

        playerEntity.setPos(this.x, this.y, this.z);
        playerEntity.setYaw(this.yaw);
        playerEntity.setPitch(this.pitch);
    }

    private Identifier resolveBaseAnimation(String baseName) {
        String[] candidates = new String[] {
                "moud:player_animations/" + baseName,
                "moud:" + baseName,
                baseName
        };
        for (String candidate : candidates) {
            Identifier id = Identifier.tryParse(candidate);
            if (id != null && PlayerAnimResources.getAnimation(id) != null) {
                return id;
            }
        }
        return Identifier.of("moud", baseName);
    }



    public double getInterpolatedX(float tickDelta) {
        return x;
    }

    public double getInterpolatedY(float tickDelta) {
        return y;
    }

    public double getInterpolatedZ(float tickDelta) {
        return z;
    }

    public float getInterpolatedYaw(float tickDelta) {
        return yaw;
    }

    public float getInterpolatedPitch(float tickDelta) {
        return pitch;
    }

    public BlockPos getBlockPos() {
        return BlockPos.ofFloored(this.x, this.y, this.z);
    }

    private enum MovementState {IDLE, WALKING}
}
