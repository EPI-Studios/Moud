package com.moud.client.animation;

import com.zigythebird.playeranim.PlayerAnimLibMod;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.easing.EasingType;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class ClientAnimationPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientAnimationPlayer.class);
    private static final Set<String> LOOPING_ANIMATIONS = Set.of("idle", "walk", "run");

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
        playAnimation(animationIdStr, false, 0);
    }

    public void playAnimationWithFade(String animationIdStr, int durationTicks) {
        playAnimation(animationIdStr, true, durationTicks);
    }

    private void playAnimation(String animationIdStr, boolean fade, int durationTicks) {
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

        boolean isLooping = LOOPING_ANIMATIONS.contains(animationId.getPath());
        Animation.LoopType loopType = isLooping ? Animation.LoopType.LOOP : Animation.LoopType.PLAY_ONCE;

        RawAnimation rawAnimation = RawAnimation.begin().then(animation, loopType);

        if (fade && durationTicks > 0) {
            AbstractFadeModifier fadeModifier = AbstractFadeModifier.standardFadeIn(durationTicks, EasingType.EASE_IN_OUT_SINE);
            controller.replaceAnimationWithFade(fadeModifier, rawAnimation);
            LOGGER.info("Fading to animation '{}' on local player over {} ticks.", animationId, durationTicks);
        } else {
            controller.triggerAnimation(rawAnimation);
            LOGGER.info("Triggering animation '{}' on local player.", animationId);
        }
    }
}