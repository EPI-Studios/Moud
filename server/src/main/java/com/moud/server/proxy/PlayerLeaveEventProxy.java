package com.moud.server.proxy;

import com.moud.server.ts.TsExpose;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;

import java.util.UUID;

@TsExpose
public class PlayerLeaveEventProxy {
    private final String username;
    private final UUID uuid;

    public PlayerLeaveEventProxy(Player player) {
        this.username = player.getUsername();
        this.uuid = player.getUuid();
    }

    @HostAccess.Export
    public String getName() {
        return username;
    }

    @HostAccess.Export
    public String getUuid() {
        return uuid.toString();
    }
}