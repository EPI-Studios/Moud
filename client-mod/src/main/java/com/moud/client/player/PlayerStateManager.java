package com.moud.client.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerStateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerStateManager.class);
    private static PlayerStateManager instance;

    private final MinecraftClient client;
    private boolean hotbarHidden = false;
    private boolean handHidden = false;
    private boolean experienceHidden = false;

    private PlayerStateManager() {
        this.client = MinecraftClient.getInstance();
    }

    public static synchronized PlayerStateManager getInstance() {
        if (instance == null) {
            instance = new PlayerStateManager();
        }
        return instance;
    }

    public void updatePlayerState(boolean hideHotbar, boolean hideHand, boolean hideExperience) {
        this.hotbarHidden = hideHotbar;
        this.handHidden = hideHand;
        this.experienceHidden = hideExperience;

        LOGGER.debug("UI state updated - hotbar: {}, hand: {}, experience: {}", hideHotbar, hideHand, hideExperience);
    }

    public boolean isHotbarHidden() {
        return hotbarHidden;
    }

    public boolean isHandHidden() {
        return handHidden;
    }

    public boolean isExperienceHidden() {
        return experienceHidden;
    }

    public void reset() {
        hotbarHidden = false;
        handHidden = false;
        experienceHidden = false;
    }
}