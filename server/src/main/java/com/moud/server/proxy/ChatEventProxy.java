package com.moud.server.proxy;

import com.moud.server.ts.TsExpose;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerChatEvent;
import org.graalvm.polyglot.HostAccess;

@TsExpose
public class ChatEventProxy {
    private final Player player;
    private final String message;
    private final PlayerChatEvent event;

    public ChatEventProxy(Player player, String message, PlayerChatEvent event) {
        this.player = player;
        this.message = message;
        this.event = event;
    }

    @HostAccess.Export
    public PlayerProxy getPlayer() {
        return new PlayerProxy(player);
    }

    @HostAccess.Export
    public String getMessage() {
        return message;
    }

    @HostAccess.Export
    public void cancel() {
        event.setCancelled(true);
    }

    @HostAccess.Export
    public boolean isCancelled() {
        return event.isCancelled();
    }
}