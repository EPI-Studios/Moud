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

        LOGGER.info("Animation {} est gérée par PlayerAnimationLibrary.", animationName);
    }

    public Animation getAnimation(String animationName) {
        if (animationName == null) return null;

        Identifier animationId = Identifier.tryParse(animationName);
        if (animationId == null) {
            LOGGER.warn("Format d'ID d'animation invalide : {}", animationName);
            return null;
        }

        Animation data = com.zigythebird.playeranim.animation.PlayerAnimResources.getAnimation(animationId);
        if (data == null) {
            LOGGER.trace("Animation '{}' not found in cache.", animationName);
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