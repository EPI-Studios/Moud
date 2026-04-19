package com.moud.client.fabric.scripting.api;

import com.moud.client.fabric.render.McHudOverrides;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import net.minecraft.client.MinecraftClient;
import java.util.HashMap;
import java.util.Map;

public final class RenderApi {

    public void setMcHudHidden(boolean hidden) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null) {
            mc.options.hudHidden = hidden;
        }
    }

    public boolean isMcHudHidden() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc != null && mc.options != null && mc.options.hudHidden;
    }

    public void setVanillaHandHidden(boolean hidden) {
        PlayRuntimeClient.scriptForceHideHand = hidden;
    }

    public boolean isVanillaHandHidden() {
        return PlayRuntimeClient.scriptForceHideHand;
    }

    public void setMcHudComponentHidden(String component, boolean hidden) {
        if (component == null) return;
        switch (component.trim().toLowerCase()) {
            case "crosshair" -> McHudOverrides.hideCrosshair = hidden;
            case "hotbar" -> McHudOverrides.hideHotbar = hidden;
            case "status_bars", "statusbars", "health", "hearts" ->
                    McHudOverrides.hideStatusBars = hidden;
            case "experience_bar", "xp", "experience" ->
                    McHudOverrides.hideExperienceBar = hidden;
            case "status_effects", "effects" ->
                    McHudOverrides.hideStatusEffects = hidden;
            case "scoreboard" -> McHudOverrides.hideScoreboard = hidden;
            case "chat" -> McHudOverrides.hideChat = hidden;
            case "player_list", "playerlist", "tab" ->
                    McHudOverrides.hidePlayerList = hidden;
            case "held_item_tooltip", "item_tooltip" ->
                    McHudOverrides.hideHeldItemTooltip = hidden;
            case "overlay_message", "action_bar", "actionbar" ->
                    McHudOverrides.hideOverlayMessage = hidden;
            case "title", "subtitle" ->
                    McHudOverrides.hideTitleAndSubtitle = hidden;
            case "vignette" -> McHudOverrides.hideVignette = hidden;
            case "boss_bar", "bossbar" -> McHudOverrides.hideBossBar = hidden;
            default -> {
            }
        }
    }

    public boolean isMcHudComponentHidden(String component) {
        if (component == null) return false;
        return switch (component.trim().toLowerCase()) {
            case "crosshair" -> McHudOverrides.hideCrosshair;
            case "hotbar" -> McHudOverrides.hideHotbar;
            case "status_bars", "statusbars", "health", "hearts" -> McHudOverrides.hideStatusBars;
            case "experience_bar", "xp", "experience" -> McHudOverrides.hideExperienceBar;
            case "status_effects", "effects" -> McHudOverrides.hideStatusEffects;
            case "scoreboard" -> McHudOverrides.hideScoreboard;
            case "chat" -> McHudOverrides.hideChat;
            case "player_list", "playerlist", "tab" -> McHudOverrides.hidePlayerList;
            case "held_item_tooltip", "item_tooltip" -> McHudOverrides.hideHeldItemTooltip;
            case "overlay_message", "action_bar", "actionbar" -> McHudOverrides.hideOverlayMessage;
            case "title", "subtitle" -> McHudOverrides.hideTitleAndSubtitle;
            case "vignette" -> McHudOverrides.hideVignette;
            case "boss_bar", "bossbar" -> McHudOverrides.hideBossBar;
            default -> false;
        };
    }

    public void resetMcHudComponents() {
        McHudOverrides.resetAll();
    }


    private final Map<Long, Map<String, Object>> uniformOverrides = new HashMap<>();

    private final Map<Long, Map<String, Object>> materialParamOverrides = new HashMap<>();

    public RenderApi() {
    }


    public void setUniform(long nodeId, String key, double value) {
        uniformOverrides.computeIfAbsent(nodeId, k -> new HashMap<>()).put(key, value);
    }

    public void setUniformVec(long nodeId, String key, double x, double y, double z, double w) {
        uniformOverrides.computeIfAbsent(nodeId, k -> new HashMap<>())
                .put(key, new double[]{x, y, z, w});
    }


    public void setMaterialParam(long nodeId, String paramName, Object value) {
        materialParamOverrides.computeIfAbsent(nodeId, k -> new HashMap<>()).put(paramName, value);
    }


    public void setTint(long nodeId, double r, double g, double b, double a) {
        materialParamOverrides.computeIfAbsent(nodeId, k -> new HashMap<>())
                .put("tint", new double[]{r, g, b, a});
    }

    public void setVisible(long nodeId, boolean visible) {
        materialParamOverrides.computeIfAbsent(nodeId, k -> new HashMap<>())
                .put("visible", visible);
    }


    public void clearFrameOverrides() {
        uniformOverrides.clear();
        materialParamOverrides.clear();
    }


    public Map<Long, Map<String, Object>> uniformOverrides() {
        return uniformOverrides;
    }

    public Map<Long, Map<String, Object>> materialParamOverrides() {
        return materialParamOverrides;
    }
}