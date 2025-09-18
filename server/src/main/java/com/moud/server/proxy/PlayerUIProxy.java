package com.moud.server.proxy;

import com.moud.server.network.ServerNetworkPackets;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;

public class PlayerUIProxy {
    private final Player player;
    private boolean hideHotbar = false;
    private boolean hideHand = false;
    private boolean hideExperience = false;

    public PlayerUIProxy(Player player) {
        this.player = player;
    }

    @HostAccess.Export
    public void hide() {
        hide(true, true, true);
    }

    @HostAccess.Export
    public void hide(boolean hideHotbar, boolean hideHand, boolean hideExperience) {
        this.hideHotbar = hideHotbar;
        this.hideHand = hideHand;
        this.hideExperience = hideExperience;

        player.sendPacket(ServerNetworkPackets.createClientboundPlayerStatePacket(
                hideHotbar, hideHand, hideExperience
        ));
    }

    @HostAccess.Export
    public void show() {
        this.hideHotbar = false;
        this.hideHand = false;
        this.hideExperience = false;

        player.sendPacket(ServerNetworkPackets.createClientboundPlayerStatePacket(
                false, false, false
        ));
    }

    public boolean isHideHotbar() {
        return hideHotbar;
    }

    public boolean isHideHand() {
        return hideHand;
    }

    public boolean isHideExperience() {
        return hideExperience;
    }
}