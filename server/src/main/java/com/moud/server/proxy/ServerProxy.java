package com.moud.server.proxy;

import net.minestom.server.MinecraftServer;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.proxy.ProxyArray;
import java.util.List;
import java.util.stream.Collectors;

public class ServerProxy {
    @HostAccess.Export
    public void broadcast(String message) {
        MinecraftServer.getConnectionManager().getOnlinePlayers()
                .forEach(player -> player.sendMessage(message));
    }

    @HostAccess.Export
    public int getPlayerCount() {
        return MinecraftServer.getConnectionManager().getOnlinePlayerCount();
    }

    @HostAccess.Export
    public ProxyArray getPlayers() {
        List<PlayerProxy> playerList = MinecraftServer.getConnectionManager().getOnlinePlayers()
                .stream()
                .map(PlayerProxy::new)
                .collect(Collectors.toList());
        return new JsArrayProxy(playerList);
    }
}