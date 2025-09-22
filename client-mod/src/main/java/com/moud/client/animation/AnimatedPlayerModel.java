package com.moud.client.animation;

import com.moud.api.math.Vector3;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.util.RenderUtil;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnimatedPlayerModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnimatedPlayerModel.class);

    private final OtherClientPlayerEntity fakePlayer;
    private final PlayerAnimationController animationController;
    private final PlayerEntityModel<OtherClientPlayerEntity> model;

    private final PlayerAnimBone pal$head = new PlayerAnimBone("head");
    private final PlayerAnimBone pal$body = new PlayerAnimBone("body");
    private final PlayerAnimBone pal$rightArm = new PlayerAnimBone("right_arm");
    private final PlayerAnimBone pal$leftArm = new PlayerAnimBone("left_arm");
    private final PlayerAnimBone pal$rightLeg = new PlayerAnimBone("right_leg");
    private final PlayerAnimBone pal$leftLeg = new PlayerAnimBone("left_leg");

    public AnimatedPlayerModel(ClientWorld world) {
        ModelPart modelPart = MinecraftClient.getInstance().getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER);
        this.model = new PlayerEntityModel<>(modelPart, false);
        this.fakePlayer = new OtherClientPlayerEntity(world, MinecraftClient.getInstance().player.getGameProfile());
        this.animationController = new PlayerAnimationController(null, (controller, state, animSetter) -> PlayState.CONTINUE);
    }

    public OtherClientPlayerEntity getFakePlayer() {
        return this.fakePlayer;
    }

    public double getX() {
        return this.fakePlayer.getX();
    }

    public double getY() {
        return this.fakePlayer.getY();
    }

    public double getZ() {
        return this.fakePlayer.getZ();
    }

    public BlockPos getBlockPos() {
        return this.fakePlayer.getBlockPos();
    }

    public void playAnimation(String animationIdStr) {
        Identifier animationId = Identifier.tryParse(animationIdStr);
        if (animationId == null) {
            LOGGER.error("Invalid animation ID format: {}", animationIdStr);
            return;
        }

        Animation animation = com.zigythebird.playeranim.animation.PlayerAnimResources.getAnimation(animationId);
        if (animation != null) {
            RawAnimation rawAnimation = RawAnimation.begin().then(animation, Animation.LoopType.LOOP);
            this.animationController.triggerAnimation(rawAnimation);
        } else {
            LOGGER.warn("Animation '{}' not found.", animationId);
        }
    }

    public void tick() {
        if (animationController.isActive()) {
            animationController.tick(new AnimationData(0, 0));
        }
    }

    public void setupAnim(float partialTick) {
        if (animationController.isActive()) {
            animationController.setupAnim(new AnimationData(0, partialTick));
            resetModelParts();
            RenderUtil.copyVanillaPart(this.model.head, pal$head);
            RenderUtil.copyVanillaPart(this.model.body, pal$body);
            RenderUtil.copyVanillaPart(this.model.rightArm, pal$rightArm);
            RenderUtil.copyVanillaPart(this.model.leftArm, pal$leftArm);
            RenderUtil.copyVanillaPart(this.model.rightLeg, pal$rightLeg);
            RenderUtil.copyVanillaPart(this.model.leftLeg, pal$leftLeg);

            pal$head.copyOtherBone(animationController.get3DTransform(pal$head));
            pal$body.copyOtherBone(animationController.get3DTransform(pal$body));
            pal$rightArm.copyOtherBone(animationController.get3DTransform(pal$rightArm));
            pal$leftArm.copyOtherBone(animationController.get3DTransform(pal$leftArm));
            pal$rightLeg.copyOtherBone(animationController.get3DTransform(pal$rightLeg));
            pal$leftLeg.copyOtherBone(animationController.get3DTransform(pal$leftLeg));

            RenderUtil.translatePartToBone(this.model.head, pal$head);
            RenderUtil.translatePartToBone(this.model.hat, pal$head);
            RenderUtil.translatePartToBone(this.model.body, pal$body);
            RenderUtil.translatePartToBone(this.model.jacket, pal$body);
            RenderUtil.translatePartToBone(this.model.rightArm, pal$rightArm);
            RenderUtil.translatePartToBone(this.model.rightSleeve, pal$rightArm);
            RenderUtil.translatePartToBone(this.model.leftArm, pal$leftArm);
            RenderUtil.translatePartToBone(this.model.leftSleeve, pal$leftArm);
            RenderUtil.translatePartToBone(this.model.rightLeg, pal$rightLeg);
            RenderUtil.translatePartToBone(this.model.rightPants, pal$rightLeg);
            RenderUtil.translatePartToBone(this.model.leftLeg, pal$leftLeg);
            RenderUtil.translatePartToBone(this.model.leftPants, pal$leftLeg);
        } else {
            model.setAngles(this.fakePlayer, 0, 0, 0, 0, 0);
        }
    }

    private void resetModelParts() {
        model.head.resetTransform();
        model.hat.resetTransform();
        model.body.resetTransform();
        model.jacket.resetTransform();
        model.rightArm.resetTransform();
        model.rightSleeve.resetTransform();
        model.leftArm.resetTransform();
        model.leftSleeve.resetTransform();
        model.rightLeg.resetTransform();
        model.rightPants.resetTransform();
        model.leftLeg.resetTransform();
        model.leftPants.resetTransform();
    }

    public PlayerEntityModel<OtherClientPlayerEntity> getModel() {
        return model;
    }

    public void updatePositionAndRotation(Vector3 position, float yaw, float pitch) {
        if (this.fakePlayer != null) {
            this.fakePlayer.setPosition(position.x, position.y, position.z);
            this.fakePlayer.setYaw(yaw);
            this.fakePlayer.setPitch(pitch);
        }
    }

    public void updateSkin(String skinUrl) {
        LOGGER.info("Skin update requested for model with URL: " + skinUrl);
    }
}