package com.moud.client.rendering;

import com.zigythebird.playeranimcore.animation.Animation;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AnimationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnimationManager.class);
    private static final AnimationManager INSTANCE = new AnimationManager();

    private final Map<String, Animation> animationCache = new ConcurrentHashMap<>();

    private AnimationManager() {}

    public static AnimationManager getInstance() {
        return INSTANCE;
    }

    public void loadAnimation(String animationName, String jsonContent) {
        LOGGER.info("Loading animation: {} with content length: {}", animationName, jsonContent.length());

        try {
            Identifier animationId = Identifier.of("moud", animationName);

            Animation existingAnimation = com.zigythebird.playeranim.animation.PlayerAnimResources.getAnimation(animationId);
            if (existingAnimation != null) {
                LOGGER.info("Animation {} already exists in PlayerAnimResources", animationId);
                animationCache.put(animationName, existingAnimation);
                return;
            }

            LOGGER.warn("Animation {} not found in PlayerAnimResources. The animation files need to be properly integrated with PlayerAnimationLib resource system.", animationId);
            LOGGER.info("Available animations should be placed in assets/moud/animations/ and follow PlayerAnimationLib format");

        } catch (Exception e) {
            LOGGER.error("Failed to load animation: {}", animationName, e);
        }
    }

    public Animation getAnimation(String animationName) {
        if (animationName == null) return null;

        Identifier animationId = Identifier.tryParse(animationName);
        if (animationId == null) {
            LOGGER.warn("Invalid animation ID format: {}", animationName);
            return null;
        }

        Animation data = com.zigythebird.playeranim.animation.PlayerAnimResources.getAnimation(animationId);
        if (data == null) {
            LOGGER.warn("Animation '{}' not found in PlayerAnimResources", animationName);

            animationId = Identifier.of("moud", animationName);
            data = com.zigythebird.playeranim.animation.PlayerAnimResources.getAnimation(animationId);

            if (data == null) {
                LOGGER.warn("Animation '{}' not found with moud namespace either", animationId);
            }
        }
        return data;
    }

    public String getAnimationName(Animation data) {
        return "unknown";
    }

    public void clearAll() {
        animationCache.clear();
        LOGGER.info("Cleared all cached animations.");
    }
}