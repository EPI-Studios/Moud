package com.moud.client.player;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerStateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerStateManager.class);
    private static PlayerStateManager instance;

    private final MinecraftClient client;
    private boolean hotbarHidden = false;
    private boolean handHidden = false;
    private boolean experienceHidden = false;
    private boolean healthHidden = false;
    private boolean foodHidden = false;
    private boolean crosshairHidden = false;
    private boolean chatHidden = false;
    private boolean playerListHidden = false;
    private boolean scoreboardHidden = false;

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

    public void updateExtendedPlayerState(boolean hideHotbar, boolean hideHand, boolean hideExperience,
                                          boolean hideHealth, boolean hideFood, boolean hideCrosshair,
                                          boolean hideChat, boolean hidePlayerList, boolean hideScoreboard) {
        this.hotbarHidden = hideHotbar;
        this.handHidden = hideHand;
        this.experienceHidden = hideExperience;
        this.healthHidden = hideHealth;
        this.foodHidden = hideFood;
        this.crosshairHidden = hideCrosshair;
        this.chatHidden = hideChat;
        this.playerListHidden = hidePlayerList;
        this.scoreboardHidden = hideScoreboard;

        LOGGER.debug("UI state updated");
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

    public boolean isHealthHidden() {
        return healthHidden;
    }

    public boolean isFoodHidden() {
        return foodHidden;
    }

    public boolean isCrosshairHidden() {
        return crosshairHidden;
    }

    public boolean isChatHidden() {
        return chatHidden;
    }

    public boolean isPlayerListHidden() {
        return playerListHidden;
    }

    public boolean isScoreboardHidden() {
        return scoreboardHidden;
    }

    public void reset() {
        hotbarHidden = false;
        handHidden = false;
        experienceHidden = false;
        healthHidden = false;
        foodHidden = false;
        crosshairHidden = false;
        chatHidden = false;
        playerListHidden = false;
        scoreboardHidden = false;
    }
}