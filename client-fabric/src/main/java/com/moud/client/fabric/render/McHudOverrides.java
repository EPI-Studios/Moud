package com.moud.client.fabric.render;

public final class McHudOverrides {
    public static volatile boolean hideCrosshair;
    public static volatile boolean hideHotbar;
    public static volatile boolean hideStatusBars;
    public static volatile boolean hideExperienceBar;
    public static volatile boolean hideStatusEffects;
    public static volatile boolean hideScoreboard;
    public static volatile boolean hideChat;
    public static volatile boolean hidePlayerList;
    public static volatile boolean hideHeldItemTooltip;
    public static volatile boolean hideOverlayMessage;
    public static volatile boolean hideTitleAndSubtitle;
    public static volatile boolean hideVignette;
    public static volatile boolean hideBossBar;

    private McHudOverrides() {}

    public static void resetAll() {
        hideCrosshair = false;
        hideHotbar = false;
        hideStatusBars = false;
        hideExperienceBar = false;
        hideStatusEffects = false;
        hideScoreboard = false;
        hideChat = false;
        hidePlayerList = false;
        hideHeldItemTooltip = false;
        hideOverlayMessage = false;
        hideTitleAndSubtitle = false;
        hideVignette = false;
        hideBossBar = false;
    }
}
