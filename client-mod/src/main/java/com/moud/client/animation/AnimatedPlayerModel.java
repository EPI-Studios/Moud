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

    private final OtherClientPlayerEntity fakePlayer;
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
        this.fakePlayer = new OtherClientPlayerEntity(world, gameProfile);
        this.modelId = gameProfile.getId().getLeastSignificantBits() ^ gameProfile.getId().getMostSignificantBits();

        this.animationController = new PlayerAnimationController(this.fakePlayer, (controller, state, animSetter) -> PlayState.CONTINUE);

        PlayerAnimManager manager = ((IAnimatedPlayer) this.fakePlayer).playerAnimLib$getAnimManager();

        manager.addAnimLayer(1000, this.animationController);

        LOGGER.debug("AnimatedPlayerModel created. Controller hash: {}", this.animationController.hashCode());
    }

    public long getModelId() {
        return modelId;
    }

    public void tick() {

        this.fakePlayer.tick();

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
            playBaseAnimation(newState);
        }
    }

    private void playBaseAnimation(MovementState state) {
        String animId = (state == MovementState.IDLE) ? "moud:idle" : "moud:walk";
        Animation animation = PlayerAnimResources.getAnimation(Identifier.of(animId));
        if (animation != null) {
            animationController.triggerAnimation(RawAnimation.begin().then(animation, Animation.LoopType.LOOP));
            LOGGER.debug("Playing base animation '{}'. Controller active: {}", animId, animationController.isActive());
        } else {
            LOGGER.warn("Base animation '{}' not found in PlayerAnimResources. Make sure it is packaged correctly.", animId);
        }
    }

    public void playAnimationWithFade(String animationIdStr, int durationTicks) {
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
            long duration = Math.max(50, (long) (animation.length() * 50));
            this.overrideAnimationEndTime = durationTicks <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + duration;

            RawAnimation newAnimation = durationTicks <= 0
                    ? RawAnimation.begin().then(animation, Animation.LoopType.LOOP)
                    : RawAnimation.begin().thenPlay(animation);
            AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn(Math.max(0, durationTicks), EasingType.EASE_IN_OUT_SINE);
            this.animationController.replaceAnimationWithFade(fade, newAnimation);

            LOGGER.debug("Triggering animation '{}' WITH FADE. DurationTicks: {} loop={}", animationId, durationTicks, durationTicks <= 0);
        } else {
            LOGGER.warn("Animation '{}' not found in PlayerAnimResources.", animationId);
        }
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
            boolean isBaseMovementAnim = "idle".equals(animationId.getPath()) || "walk".equals(animationId.getPath());

            if (isBaseMovementAnim) {
                this.overrideAnimation = null;
                playBaseAnimation("walk".equals(animationId.getPath()) ? MovementState.WALKING : MovementState.IDLE);
                LOGGER.debug("Triggering base animation '{}'.", animationId);
            } else {
                this.overrideAnimation = animationIdStr;
                long duration = Math.max(50, (long) (animation.length() * 50));
                this.overrideAnimationEndTime = System.currentTimeMillis() + duration;
                animationController.triggerAnimation(RawAnimation.begin().thenPlay(animation));
                LOGGER.debug("Triggering one-shot animation '{}'. Duration: {}ms", animationId, duration);
            }
        } else {
            LOGGER.warn("Animation '{}' not found in PlayerAnimResources.", animationId);
        }
    }

    public void setupAnim(float partialTick) {
        fakePlayer.setPos(getInterpolatedX(partialTick), getInterpolatedY(partialTick), getInterpolatedZ(partialTick));
        fakePlayer.setYaw(getInterpolatedYaw(partialTick));
        fakePlayer.setPitch(getInterpolatedPitch(partialTick));
        fakePlayer.headYaw = fakePlayer.getYaw();
        fakePlayer.bodyYaw = fakePlayer.getYaw();

        model.setAngles(fakePlayer, 0, 0, 0, fakePlayer.getYaw(), fakePlayer.getPitch());
        LOGGER.trace("SetupAnim called for Model. Controller Active: {}", animationController.isActive());

    }

    public OtherClientPlayerEntity getFakePlayer() {
        return this.fakePlayer;
    }

    public PlayerEntityModel<OtherClientPlayerEntity> getModel() {
        return model;
    }

    public void updateSkin(String skinUrl) {
        if (skinUrl == null || skinUrl.isEmpty()) {
            LOGGER.warn("Empty skin URL provided for player model");
            return;
        }
        try {
            GameProfile gameProfile = this.fakePlayer.getGameProfile();
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
        if (!hasPosition) {
            this.prevX = position.x;
            this.prevY = position.y;
            this.prevZ = position.z;
            this.prevYaw = yaw;
            this.prevPitch = pitch;
            this.hasPosition = true;
        } else {
            this.prevX = this.x;
            this.prevY = this.y;
            this.prevZ = this.z;
            this.prevYaw = this.yaw;
            this.prevPitch = this.pitch;
        }
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        this.yaw = yaw;
        this.pitch = pitch;

        fakePlayer.setPos(this.x, this.y, this.z);
        fakePlayer.setYaw(yaw);
        fakePlayer.setPitch(pitch);
    }

    public double getInterpolatedX(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevX, x);
    }

    public double getInterpolatedY(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevY, y);
    }

    public double getInterpolatedZ(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevZ, z);
    }

    public float getInterpolatedYaw(float tickDelta) {
        return MathHelper.lerpAngleDegrees(tickDelta, prevYaw, yaw);
    }

    public float getInterpolatedPitch(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevPitch, pitch);
    }

    public BlockPos getBlockPos() {
        return BlockPos.ofFloored(this.x, this.y, this.z);
    }

    private enum MovementState {IDLE, WALKING}
}
