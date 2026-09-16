package com.meekdev.moud.mod.server;

import com.meekdev.moud.script.api.PlayerRef;
import com.meekdev.moud.script.api.RosterRef;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class Roster implements RosterRef {

    public static final Roster INSTANCE = new Roster();

    private Roster() {}

    @Override
    public List<PlayerRef> all() {
        MinecraftServer server = ServerScene.server();
        List<PlayerRef> players = new ArrayList<>();
        if (server == null) return players;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) players.add(new JoinedPlayer(player));
        return players;
    }

    @Override
    public PlayerRef find(String id) {
        MinecraftServer server = ServerScene.server();
        if (server == null) return null;
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(id));
            return player == null ? null : new JoinedPlayer(player);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
