package com.moud.server.proxy;

import com.moud.server.network.ServerNetworkPackets;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public class PlayerUIProxy {
    private final Player player;

    public PlayerUIProxy(Player player) {
        this.player = player;
    }

    @HostAccess.Export
    public void hide() {
        sendStatePacket(true, true, true);
    }

    @HostAccess.Export
    public void hide(Value options) {
        if (options == null || !options.hasMembers()) {
            hide();
            return;
        }

        boolean hideHotbar = options.hasMember("hideHotbar") ? options.getMember("hideHotbar").asBoolean() : false;
        boolean hideHand = options.hasMember("hideHand") ? options.getMember("hideHand").asBoolean() : false;
        boolean hideExperience = options.hasMember("hideExperience") ? options.getMember("hideExperience").asBoolean() : false;

        sendStatePacket(hideHotbar, hideHand, hideExperience);
    }

    @HostAccess.Export
    public void show() {
        sendStatePacket(false, false, false);
    }

    private void sendStatePacket(boolean hideHotbar, boolean hideHand, boolean hideExperience) {
        player.sendPacket(ServerNetworkPackets.createClientboundPlayerStatePacket(
                hideHotbar, hideHand, hideExperience
        ));
    }
}