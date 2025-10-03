package com.moud.client.animation;

import com.zigythebird.playeranim.PlayerAnimLibMod;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientAnimationPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientAnimationPlayer.class);

    private final AbstractClientPlayerEntity player;
    private AnimationController animationController;

    public ClientAnimationPlayer(AbstractClientPlayerEntity player) {
        this.player = player;
    }

    private AnimationController getController() {
        if (this.animationController == null) {
            try {
                this.animationController = (AnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(player, PlayerAnimLibMod.ANIMATION_LAYER_ID);
                LOGGER.debug("ClientAnimationPlayer controller retrieved. Hash: {}", this.animationController.hashCode());
            } catch (Exception e) {
                LOGGER.error("Failed to retrieve PlayerAnimationController for local player.", e);
            }
        }
        return this.animationController;
    }

    public void playAnimation(String animationIdStr) {
        AnimationController controller = getController();
        if (controller == null) {
            LOGGER.error("Could not get animation controller for player '{}'.", player.getName().getString());
            return;
        }

        Identifier animationId = Identifier.tryParse(animationIdStr);
        if (animationId == null) {
            animationId = Identifier.of("moud", animationIdStr);
        }

        Animation animation = PlayerAnimResources.getAnimation(animationId);

        if (animation == null) {
            LOGGER.warn("Animation '{}' not found in PlayerAnimResources.", animationId);
            return;
        }

        controller.triggerAnimation(RawAnimation.begin().thenPlay(animation));
        LOGGER.info("Triggering animation '{}' on local player.", animationId);
    }
}