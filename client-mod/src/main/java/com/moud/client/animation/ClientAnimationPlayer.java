package com.moud.client.animation;

import com.zigythebird.playeranim.PlayerAnimLibMod;
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
    public static final Identifier ANIMATION_LAYER_ID = Identifier.of("moud", "player_animation_layer");

    private final AbstractClientPlayerEntity player;
    private AnimationController animationController;

    public ClientAnimationPlayer(AbstractClientPlayerEntity player) {
        this.player = player;

    }

    private AnimationController getController() {
        if (this.animationController == null) {
            this.animationController = (AnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(player, PlayerAnimLibMod.ANIMATION_LAYER_ID);
        }
        return this.animationController;
    }

    public void playAnimation(String animationIdStr) {
        AnimationController controller = getController();
        if (controller == null) {
            LOGGER.error("Could not get animation controller for player.");
            return;
        }

        Identifier animationId = Identifier.tryParse(animationIdStr);
        if (animationId == null) {
            LOGGER.error("Invalid animation ID format: {}", animationIdStr);
            return;
        }

        Animation animation = com.zigythebird.playeranim.animation.PlayerAnimResources.getAnimation(animationId);
        if (animation == null) {
            LOGGER.warn("Animation '{}' not found on client.", animationId);
            return;
        }

        controller.triggerAnimation(RawAnimation.begin().thenPlay(animation));
        LOGGER.info("Playing animation '{}' on player.", animationId);
    }
}