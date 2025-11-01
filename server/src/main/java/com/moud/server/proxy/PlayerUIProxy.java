package com.moud.server.proxy;

import com.moud.network.MoudPackets;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.network.ServerPacketWrapper;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public class PlayerUIProxy {
    private final Player player;
    private boolean hideHotbar = false;
    private boolean hideHand = false;
    private boolean hideExperience = false;
    private boolean hideHealth = false;
    private boolean hideFood = false;
    private boolean hideCrosshair = false;
    private boolean hideChat = false;
    private boolean hidePlayerList = false;
    private boolean hideScoreboard = false;

    public PlayerUIProxy(Player player) {
        this.player = player;
    }

    @HostAccess.Export
    public void hide() {
        setAll(true);
    }

    @HostAccess.Export
    public void hide(Value options) {
        if (options == null || !options.hasMembers()) {
            hide();
            return;
        }

        if (options.hasMember("hotbar")) hideHotbar = options.getMember("hotbar").asBoolean();
        if (options.hasMember("hand")) hideHand = options.getMember("hand").asBoolean();
        if (options.hasMember("experience")) hideExperience = options.getMember("experience").asBoolean();
        if (options.hasMember("health")) hideHealth = options.getMember("health").asBoolean();
        if (options.hasMember("food")) hideFood = options.getMember("food").asBoolean();
        if (options.hasMember("crosshair")) hideCrosshair = options.getMember("crosshair").asBoolean();
        if (options.hasMember("chat")) hideChat = options.getMember("chat").asBoolean();
        if (options.hasMember("playerList")) hidePlayerList = options.getMember("playerList").asBoolean();
        if (options.hasMember("scoreboard")) hideScoreboard = options.getMember("scoreboard").asBoolean();

        sendStatePacket();
    }

    @HostAccess.Export
    public void show() {
        setAll(false);
    }

    @HostAccess.Export
    public void show(Value options) {
        if (options == null || !options.hasMembers()) {
            show();
            return;
        }

        if (options.hasMember("hotbar")) hideHotbar = !options.getMember("hotbar").asBoolean();
        if (options.hasMember("hand")) hideHand = !options.getMember("hand").asBoolean();
        if (options.hasMember("experience")) hideExperience = !options.getMember("experience").asBoolean();
        if (options.hasMember("health")) hideHealth = !options.getMember("health").asBoolean();
        if (options.hasMember("food")) hideFood = !options.getMember("food").asBoolean();
        if (options.hasMember("crosshair")) hideCrosshair = !options.getMember("crosshair").asBoolean();
        if (options.hasMember("chat")) hideChat = !options.getMember("chat").asBoolean();
        if (options.hasMember("playerList")) hidePlayerList = !options.getMember("playerList").asBoolean();
        if (options.hasMember("scoreboard")) hideScoreboard = !options.getMember("scoreboard").asBoolean();

        sendStatePacket();
    }

    @HostAccess.Export
    public void hideHotbar() {
        hideHotbar = true;
        sendStatePacket();
    }

    @HostAccess.Export
    public void showHotbar() {
        hideHotbar = false;
        sendStatePacket();
    }

    @HostAccess.Export
    public void hideHealth() {
        hideHealth = true;
        sendStatePacket();
    }

    @HostAccess.Export
    public void showHealth() {
        hideHealth = false;
        sendStatePacket();
    }

    @HostAccess.Export
    public void hideFood() {
        hideFood = true;
        sendStatePacket();
    }

    @HostAccess.Export
    public void showFood() {
        hideFood = false;
        sendStatePacket();
    }

    @HostAccess.Export
    public void hideExperience() {
        hideExperience = true;
        sendStatePacket();
    }

    @HostAccess.Export
    public void showExperience() {
        hideExperience = false;
        sendStatePacket();
    }

    @HostAccess.Export
    public void hideHand() {
        hideHand = true;
        sendStatePacket();
    }

    @HostAccess.Export
    public void showHand() {
        hideHand = false;
        sendStatePacket();
    }

    @HostAccess.Export
    public void hideCrosshair() {
        hideCrosshair = true;
        sendStatePacket();
    }

    @HostAccess.Export
    public void showCrosshair() {
        hideCrosshair = false;
        sendStatePacket();
    }

    @HostAccess.Export
    public void hideChat() {
        hideChat = true;
        sendStatePacket();
    }

    @HostAccess.Export
    public void showChat() {
        hideChat = false;
        sendStatePacket();
    }

    @HostAccess.Export
    public void hidePlayerList() {
        hidePlayerList = true;
        sendStatePacket();
    }

    @HostAccess.Export
    public void showPlayerList() {
        hidePlayerList = false;
        sendStatePacket();
    }

    @HostAccess.Export
    public void hideScoreboard() {
        hideScoreboard = true;
        sendStatePacket();
    }

    @HostAccess.Export
    public void showScoreboard() {
        hideScoreboard = false;
        sendStatePacket();
    }

    @HostAccess.Export
    public boolean isHotbarHidden() { return hideHotbar; }

    @HostAccess.Export
    public boolean isHealthHidden() { return hideHealth; }

    @HostAccess.Export
    public boolean isFoodHidden() { return hideFood; }

    @HostAccess.Export
    public boolean isExperienceHidden() { return hideExperience; }

    @HostAccess.Export
    public boolean isHandHidden() { return hideHand; }

    @HostAccess.Export
    public boolean isCrosshairHidden() { return hideCrosshair; }

    @HostAccess.Export
    public boolean isChatHidden() { return hideChat; }

    @HostAccess.Export
    public boolean isPlayerListHidden() { return hidePlayerList; }

    @HostAccess.Export
    public boolean isScoreboardHidden() { return hideScoreboard; }

    private void setAll(boolean hidden) {
        hideHotbar = hidden;
        hideHand = hidden;
        hideExperience = hidden;
        hideHealth = hidden;
        hideFood = hidden;
        hideCrosshair = hidden;
        hideChat = hidden;
        hidePlayerList = hidden;
        hideScoreboard = hidden;
        sendStatePacket();
    }

    private void sendStatePacket() {
        MoudPackets.ExtendedPlayerStatePacket packet = new MoudPackets.ExtendedPlayerStatePacket(
                hideHotbar, hideHand, hideExperience, hideHealth, hideFood,
                hideCrosshair, hideChat, hidePlayerList, hideScoreboard
        );
        ServerNetworkManager manager = ServerNetworkManager.getInstance();
        if (manager != null) {
            manager.send(player, packet);
        } else {
            player.sendPacket(ServerPacketWrapper.createPacket(packet));
        }
    }
}
